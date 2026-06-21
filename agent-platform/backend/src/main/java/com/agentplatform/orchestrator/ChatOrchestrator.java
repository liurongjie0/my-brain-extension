package com.agentplatform.orchestrator;

import com.agentplatform.agent.AgentEntity;
import com.agentplatform.agent.AgentKnowledgeBaseEntity;
import com.agentplatform.agent.AgentKnowledgeBaseRepository;
import com.agentplatform.agent.AgentRepository;
import com.agentplatform.agent.AgentToolEntity;
import com.agentplatform.agent.AgentToolRepository;
import com.agentplatform.chat.ConversationEntity;
import com.agentplatform.chat.ConversationRepository;
import com.agentplatform.chat.MessageEntity;
import com.agentplatform.chat.MessageRepository;
import com.agentplatform.chat.MessageRole;
import com.agentplatform.common.BusinessException;
import com.agentplatform.mcp.McpTools;
import com.agentplatform.model.ChatModelFactory;
import com.agentplatform.model.ModelConfigEntity;
import com.agentplatform.model.ModelConfigRepository;
import com.agentplatform.rag.RagRetriever;
import com.agentplatform.rag.dto.RetrieveResult;
import com.agentplatform.tool.PlanToolCallback;
import com.agentplatform.tool.ToolCallbackFactory;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates a chat turn. The actual think→act→observe control flow is a Spring AI Alibaba
 * {@link AgentGraph} (nodes + shared state + conditional routing) — one unified graph serves
 * every agent type. This class owns the surrounding I/O: conversation/message persistence,
 * RAG context building, model/tool resolution, and adapting the graph's progress into the
 * SSE {@link ChatChunk} event stream (meta/source/think/token/step/plan/done/error).
 */
@Service
public class ChatOrchestrator {

    private static final int RAG_TOP_K = 4;
    private static final String AGENT_TYPE_REACT = "react";
    private static final String PLAN_HINT =
            "你可以使用 write_todos 工具维护一个任务清单。开始多步骤任务前，先用它列出步骤"
            + "（status 取 pending / in_progress / completed）；每推进一步就再次调用它、发送完整清单并更新状态。"
            + "只在任务确实需要多步时使用，简单问题直接回答。";

    /** built-in plan-as-a-tool callback, injected for react agents with plan mode on */
    private final ToolCallback planTool = new PlanToolCallback();

    private final ChatModel chatModel;
    private final AgentRepository agents;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final AgentKnowledgeBaseRepository bindings;
    private final RagRetriever retriever;
    private final AgentToolRepository agentTools;
    private final ToolCallbackFactory toolCallbackFactory;
    private final ObjectMapper objectMapper;
    private final ModelConfigRepository modelConfigs;
    private final ChatModelFactory chatModelFactory;
    private final McpTools mcpTools;
    private final AgentGraph agentGraph;

    public ChatOrchestrator(ChatModel chatModel, AgentRepository agents,
                            ConversationRepository conversations, MessageRepository messages,
                            AgentKnowledgeBaseRepository bindings, RagRetriever retriever,
                            AgentToolRepository agentTools, ToolCallbackFactory toolCallbackFactory,
                            ObjectMapper objectMapper, ModelConfigRepository modelConfigs,
                            ChatModelFactory chatModelFactory, McpTools mcpTools, AgentGraph agentGraph) {
        this.chatModel = chatModel;
        this.agents = agents;
        this.conversations = conversations;
        this.messages = messages;
        this.bindings = bindings;
        this.retriever = retriever;
        this.agentTools = agentTools;
        this.toolCallbackFactory = toolCallbackFactory;
        this.objectMapper = objectMapper;
        this.modelConfigs = modelConfigs;
        this.chatModelFactory = chatModelFactory;
        this.mcpTools = mcpTools;
        this.agentGraph = agentGraph;
    }

    public Flux<ChatChunk> chat(Long agentId, Long conversationId, String userMessage, String userId) {
        AgentEntity agent = agents.findById(agentId)
                .orElseThrow(() -> new BusinessException(40401, "agent not found"));
        if (Boolean.FALSE.equals(agent.getEnabled())) {
            throw new BusinessException(40301, "agent disabled");
        }

        ConversationEntity conv = resolveConversation(agentId, conversationId, userId, userMessage);
        Long convId = conv.getId();
        saveMessage(convId, MessageRole.USER, userMessage, null, null);

        BuiltContext ctx = buildContext(agent, convId, userMessage);

        // tools = HTTP-bound tools + MCP tools (+ the built-in planning tool for plan-mode react agents)
        List<Long> toolIds = agentTools.findByAgentId(agent.getId()).stream()
                .map(AgentToolEntity::getToolId).toList();
        List<ToolCallback> toolCallbacks = new ArrayList<>(toolCallbackFactory.build(toolIds));
        toolCallbacks.addAll(mcpTools.forAgent(agent.getId()));
        boolean planEnabled = AGENT_TYPE_REACT.equals(agent.getAgentType())
                && Boolean.TRUE.equals(agent.getPlanEnabled());
        if (planEnabled) {
            toolCallbacks.add(planTool);
        }

        ResolvedModel rm = resolveModel(agent);
        // manual tool execution: the graph's tools node runs them and surfaces each step
        OpenAiChatOptions.Builder ob = baseOptions(agent, rm.modelName()).internalToolExecutionEnabled(false);
        if (!toolCallbacks.isEmpty()) {
            ob.toolCallbacks(toolCallbacks);
        }
        OpenAiChatOptions options = ob.build();

        List<Message> seed = new ArrayList<>(ctx.messages());
        if (planEnabled) {
            seed.add(new SystemMessage(PLAN_HINT));
        }

        // drive the graph inside the SSE stream: meta + sources up front, then the graph runs
        // (streaming tokens/steps to the sink as it goes), then persist + done. Errors persist
        // whatever was produced so the turn isn't lost.
        return Flux.create(sink -> {
            AgentGraph.Run run = new AgentGraph.Run();
            AtomicBoolean cancelled = new AtomicBoolean(false);
            Sinks.Empty<Void> cancelSink = Sinks.empty();
            // client "stop" / disconnect → flip the flag and complete the cancel signal so the
            // graph's agent node truncates its model stream and the loop routes straight to END
            sink.onCancel(() -> { cancelled.set(true); cancelSink.tryEmitEmpty(); });
            try {
                sink.next(new ChatChunk(convId, ChatEvents.META, null));
                for (ChatChunk s : sourceChunks(convId, ctx.sources())) {
                    sink.next(s);
                }

                AgentGraph.Ctx gctx = new AgentGraph.Ctx(convId, rm.model(), options, sink::next,
                        run, cancelled, cancelSink.asMono());
                CompiledGraph graph = agentGraph.build(gctx);
                Map<String, Object> input = new HashMap<>();
                input.put(AgentGraph.MESSAGES, seed);
                input.put(AgentGraph.STEPS, 0);
                graph.invoke(input, RunnableConfig.builder().threadId(String.valueOf(convId)).build());

                if (cancelled.get()) {
                    persistIfProduced(convId, run);  // client gone — keep only real output, no done/error
                    sink.complete();
                    return;
                }
                persistAssistant(convId, run.finalText(), toJson(run.trajectory()), run.usage());
                sink.next(new ChatChunk(convId, ChatEvents.DONE, null));
                sink.complete();
            } catch (Exception e) {
                if (cancelled.get()) {
                    persistIfProduced(convId, run);
                    sink.complete();
                    return;
                }
                persistAssistant(convId, run.finalText(), toJson(run.trajectory()), run.usage());
                sink.next(new ChatChunk(convId, ChatEvents.ERROR, friendly(e)));
                sink.complete();
            }
        });
    }

    /** Resolved model endpoint + model id for an agent (per its model config, or the global default). */
    private record ResolvedModel(ChatModel model, String modelName) {}

    private ResolvedModel resolveModel(AgentEntity agent) {
        if (agent.getModelConfigId() != null) {
            ModelConfigEntity cfg = modelConfigs.findById(agent.getModelConfigId()).orElse(null);
            if (cfg != null) {
                return new ResolvedModel(chatModelFactory.forConfig(cfg), cfg.getModel());
            }
        }
        return new ResolvedModel(chatModel, agent.getModel());
    }

    private OpenAiChatOptions.Builder baseOptions(AgentEntity agent, String modelName) {
        return OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(agent.getTemperature())
                .maxTokens(agent.getMaxTokens())
                .topP(agent.getTopP());
    }

    private List<ChatChunk> sourceChunks(Long convId, List<RetrieveResult> sources) {
        List<ChatChunk> out = new ArrayList<>();
        for (RetrieveResult s : sources) {
            out.add(new ChatChunk(convId, ChatEvents.SOURCE, truncate(s.content(), 160)));
        }
        return out;
    }

    /** Built prompt messages plus the RAG sources used, so the client can show citations. */
    private record BuiltContext(List<Message> messages, List<RetrieveResult> sources) {}

    private BuiltContext buildContext(AgentEntity agent, Long convId, String userMessage) {
        List<Message> msgs = new ArrayList<>();
        if (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
            msgs.add(new SystemMessage(agent.getSystemPrompt()));
        }
        for (MessageEntity m : messages.findByConversationIdOrderByCreatedAtAsc(convId)) {
            switch (m.getRole()) {
                case MessageRole.USER -> msgs.add(new UserMessage(m.getContent()));
                case MessageRole.ASSISTANT -> msgs.add(new AssistantMessage(m.getContent()));
                case MessageRole.SYSTEM -> msgs.add(new SystemMessage(m.getContent()));
                default -> { }
            }
        }
        List<RetrieveResult> sources = List.of();
        List<Long> kbIds = bindings.findByAgentId(agent.getId()).stream()
                .map(AgentKnowledgeBaseEntity::getKbId).toList();
        // skip RAG gracefully when retrieval is unavailable, so a KB-bound agent still chats
        if (!kbIds.isEmpty() && retriever.isEnabled()) {
            List<RetrieveResult> hits = retriever.retrieve(kbIds, userMessage, RAG_TOP_K);
            if (!hits.isEmpty()) {
                sources = hits;
                StringBuilder ctx = new StringBuilder("参考资料(请优先依据以下内容回答):\n");
                for (RetrieveResult h : hits) {
                    ctx.append("- ").append(h.content()).append("\n");
                }
                msgs.add(new SystemMessage(ctx.toString()));
            }
        }
        return new BuiltContext(msgs, sources);
    }

    private ConversationEntity resolveConversation(Long agentId, Long conversationId,
                                                   String userId, String userMessage) {
        if (conversationId != null) {
            return conversations.findById(conversationId)
                    .orElseThrow(() -> new BusinessException(40402, "conversation not found"));
        }
        ConversationEntity c = new ConversationEntity();
        c.setAgentId(agentId);
        c.setUserId(userId);
        c.setTitle(truncate(userMessage, 20));
        return conversations.save(c);
    }

    private void persistAssistant(Long convId, String content, String toolCallsJson, Integer tokenUsage) {
        saveMessage(convId, MessageRole.ASSISTANT, content, toolCallsJson, tokenUsage);
        conversations.touch(convId);
    }

    /**
     * On a cancelled turn, persist only if something was actually produced — avoids leaving an
     * empty/half assistant message that would pollute the next turn's context.
     */
    private void persistIfProduced(Long convId, AgentGraph.Run run) {
        if (!run.finalText().isBlank() || !run.trajectory().isEmpty()) {
            persistAssistant(convId, run.finalText(), toJson(run.trajectory()), run.usage());
        }
    }

    private void saveMessage(Long convId, String role, String content, String toolCallsJson, Integer tokenUsage) {
        MessageEntity m = new MessageEntity();
        m.setConversationId(convId);
        m.setRole(role);
        m.setContent(content);
        m.setToolCallsJson(toolCallsJson);
        m.setTokenUsage(tokenUsage);
        messages.save(m);
    }

    private String toJson(List<Map<String, Object>> trajectory) {
        if (trajectory.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(trajectory);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String friendly(Throwable err) {
        String msg = err.getMessage();
        return "对话出错：" + (msg != null ? msg : err.getClass().getSimpleName());
    }
}
