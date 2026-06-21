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
import com.agentplatform.tool.ToolCallbackFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates a chat turn. Non-react agents stream the response with the framework's
 * internal tool execution; react agents run a manual tool-calling loop that surfaces each
 * step incrementally over SSE and persists the full trajectory. RAG context is injected for
 * any agent bound to knowledge bases, and the retrieved sources are surfaced to the client.
 */
@Service
public class ChatOrchestrator {

    private static final int MAX_STEPS = 8;
    private static final int RAG_TOP_K = 4;
    private static final String AGENT_TYPE_REACT = "react";

    private final ChatModel chatModel;
    private final AgentRepository agents;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final AgentKnowledgeBaseRepository bindings;
    private final RagRetriever retriever;
    private final AgentToolRepository agentTools;
    private final ToolCallbackFactory toolCallbackFactory;
    private final ToolCallingManager toolCallingManager;
    private final ObjectMapper objectMapper;
    private final ModelConfigRepository modelConfigs;
    private final ChatModelFactory chatModelFactory;
    private final McpTools mcpTools;

    public ChatOrchestrator(ChatModel chatModel, AgentRepository agents,
                            ConversationRepository conversations, MessageRepository messages,
                            AgentKnowledgeBaseRepository bindings, RagRetriever retriever,
                            AgentToolRepository agentTools, ToolCallbackFactory toolCallbackFactory,
                            ToolCallingManager toolCallingManager, ObjectMapper objectMapper,
                            ModelConfigRepository modelConfigs, ChatModelFactory chatModelFactory,
                            McpTools mcpTools) {
        this.chatModel = chatModel;
        this.agents = agents;
        this.conversations = conversations;
        this.messages = messages;
        this.bindings = bindings;
        this.retriever = retriever;
        this.agentTools = agentTools;
        this.toolCallbackFactory = toolCallbackFactory;
        this.toolCallingManager = toolCallingManager;
        this.objectMapper = objectMapper;
        this.modelConfigs = modelConfigs;
        this.chatModelFactory = chatModelFactory;
        this.mcpTools = mcpTools;
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
        List<Long> toolIds = agentTools.findByAgentId(agent.getId()).stream()
                .map(AgentToolEntity::getToolId).toList();
        List<ToolCallback> toolCallbacks = new ArrayList<>(toolCallbackFactory.build(toolIds));
        toolCallbacks.addAll(mcpTools.forAgent(agent.getId()));
        ResolvedModel rm = resolveModel(agent);

        if (AGENT_TYPE_REACT.equals(agent.getAgentType()) && !toolCallbacks.isEmpty()) {
            return reactChat(agent, convId, ctx, toolCallbacks, rm);
        }
        return streamChat(agent, convId, ctx, toolCallbacks, rm);
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

    // ===== streaming path (chat / rag / tool, framework internal tool execution) =====

    private Flux<ChatChunk> streamChat(AgentEntity agent, Long convId, BuiltContext ctx,
                                       List<ToolCallback> toolCallbacks, ResolvedModel rm) {
        OpenAiChatOptions.Builder ob = baseOptions(agent, rm.modelName());
        if (!toolCallbacks.isEmpty()) {
            ob.toolCallbacks(toolCallbacks);
        }
        Prompt prompt = new Prompt(ctx.messages(), ob.build());

        StringBuilder full = new StringBuilder();
        AtomicInteger tokenUsage = new AtomicInteger(0);

        Flux<ChatChunk> head = Flux.fromIterable(headChunks(convId, ctx.sources()));
        Flux<ChatChunk> tokens = rm.model().stream(prompt)
                .doOnNext(resp -> captureUsage(resp, tokenUsage))
                .map(resp -> {
                    String text = resp.getResult().getOutput().getText();
                    return text != null ? text : "";
                })
                .filter(s -> !s.isEmpty())
                .doOnNext(full::append)
                .map(s -> new ChatChunk(convId, ChatEvents.TOKEN, s));
        Flux<ChatChunk> done = Flux.defer(() -> {
            persistAssistant(convId, full.toString(), null, tokenUsage.get());
            return Flux.just(new ChatChunk(convId, ChatEvents.DONE, null));
        });

        return Flux.concat(head, tokens, done)
                .onErrorResume(err -> {
                    // persist whatever streamed so the turn isn't lost, then surface the error
                    persistAssistant(convId, full.toString(), null, tokenUsage.get());
                    return Flux.just(new ChatChunk(convId, ChatEvents.ERROR, friendly(err)));
                });
    }

    // ===== react path (manual tool-calling loop, incremental step events + trajectory) =====

    private Flux<ChatChunk> reactChat(AgentEntity agent, Long convId, BuiltContext ctx,
                                      List<ToolCallback> toolCallbacks, ResolvedModel rm) {
        return Flux.create(sink -> {
            StringBuilder finalText = new StringBuilder();
            List<Map<String, Object>> trajectory = new ArrayList<>();
            try {
                sink.next(new ChatChunk(convId, ChatEvents.META, null));
                for (ChatChunk s : sourceChunks(convId, ctx.sources())) {
                    sink.next(s);
                }

                OpenAiChatOptions options = baseOptions(agent, rm.modelName())
                        .toolCallbacks(toolCallbacks)
                        .internalToolExecutionEnabled(false)
                        .build();

                Prompt prompt = new Prompt(new ArrayList<>(ctx.messages()), options);
                ChatResponse response = rm.model().call(prompt);
                int steps = 0;

                while (response.hasToolCalls() && steps < MAX_STEPS) {
                    List<AssistantMessage.ToolCall> calls = response.getResult().getOutput().getToolCalls();
                    ToolExecutionResult execResult = toolCallingManager.executeToolCalls(prompt, response);
                    Map<String, String> resultsById = extractToolResults(execResult);

                    for (AssistantMessage.ToolCall call : calls) {
                        String result = resultsById.getOrDefault(call.id(), "");
                        String shortResult = truncate(result, 300);
                        String args = call.arguments() == null ? "" : call.arguments();
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("tool", call.name());
                        entry.put("args", args);
                        entry.put("result", shortResult);
                        // emit the step as structured JSON so the client can render a tool-call
                        // list with per-tool args/result drill-down (not a flat pre-joined string)
                        String stepJson;
                        try {
                            stepJson = objectMapper.writeValueAsString(entry);
                        } catch (Exception e) {
                            stepJson = "调用 " + call.name() + "(" + args + ") => " + shortResult;
                        }
                        sink.next(new ChatChunk(convId, ChatEvents.STEP, stepJson));
                        trajectory.add(entry);
                    }

                    prompt = new Prompt(execResult.conversationHistory(), options);
                    response = rm.model().call(prompt);
                    steps++;
                }

                String text = response.getResult().getOutput().getText();
                if (text != null) finalText.append(text);
                sink.next(new ChatChunk(convId, ChatEvents.TOKEN, finalText.toString()));

                persistAssistant(convId, finalText.toString(), toJson(trajectory), usageOf(response));
                sink.next(new ChatChunk(convId, ChatEvents.DONE, null));
                sink.complete();
            } catch (Exception e) {
                persistAssistant(convId, finalText.toString(), toJson(trajectory), null);
                sink.next(new ChatChunk(convId, ChatEvents.ERROR, friendly(e)));
                sink.complete();
            }
        });
    }

    // ===== helpers =====

    private List<ChatChunk> headChunks(Long convId, List<RetrieveResult> sources) {
        List<ChatChunk> head = new ArrayList<>();
        head.add(new ChatChunk(convId, ChatEvents.META, null));
        head.addAll(sourceChunks(convId, sources));
        return head;
    }

    private List<ChatChunk> sourceChunks(Long convId, List<RetrieveResult> sources) {
        List<ChatChunk> out = new ArrayList<>();
        for (RetrieveResult s : sources) {
            out.add(new ChatChunk(convId, ChatEvents.SOURCE, truncate(s.content(), 160)));
        }
        return out;
    }

    private Map<String, String> extractToolResults(ToolExecutionResult execResult) {
        Map<String, String> byId = new HashMap<>();
        for (Message m : execResult.conversationHistory()) {
            if (m instanceof ToolResponseMessage trm) {
                for (ToolResponseMessage.ToolResponse r : trm.getResponses()) {
                    byId.put(r.id(), r.responseData());
                }
            }
        }
        return byId;
    }

    private OpenAiChatOptions.Builder baseOptions(AgentEntity agent, String modelName) {
        return OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(agent.getTemperature())
                .maxTokens(agent.getMaxTokens())
                .topP(agent.getTopP());
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

    private void saveMessage(Long convId, String role, String content, String toolCallsJson, Integer tokenUsage) {
        MessageEntity m = new MessageEntity();
        m.setConversationId(convId);
        m.setRole(role);
        m.setContent(content);
        m.setToolCallsJson(toolCallsJson);
        m.setTokenUsage(tokenUsage);
        messages.save(m);
    }

    private void captureUsage(ChatResponse resp, AtomicInteger holder) {
        Integer total = usageOf(resp);
        if (total != null) holder.set(total);
    }

    private Integer usageOf(ChatResponse resp) {
        if (resp == null || resp.getMetadata() == null) return null;
        Usage usage = resp.getMetadata().getUsage();
        return usage != null ? usage.getTotalTokens() : null;
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
