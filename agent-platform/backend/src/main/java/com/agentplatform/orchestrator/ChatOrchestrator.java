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
import com.agentplatform.common.BusinessException;
import com.agentplatform.rag.RagRetriever;
import com.agentplatform.rag.dto.RetrieveResult;
import com.agentplatform.tool.ToolCallbackFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
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

/**
 * Orchestrates a chat turn. Non-react agents stream the response with the framework's
 * internal tool execution; react agents run a manual tool-calling loop that surfaces each
 * step over SSE and persists the full trajectory. RAG context is injected for any agent
 * bound to knowledge bases.
 */
@Service
public class ChatOrchestrator {

    private static final int MAX_STEPS = 8;

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

    public ChatOrchestrator(ChatModel chatModel, AgentRepository agents,
                            ConversationRepository conversations, MessageRepository messages,
                            AgentKnowledgeBaseRepository bindings, RagRetriever retriever,
                            AgentToolRepository agentTools, ToolCallbackFactory toolCallbackFactory,
                            ToolCallingManager toolCallingManager, ObjectMapper objectMapper) {
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
    }

    public Flux<ChatChunk> chat(Long agentId, Long conversationId, String userMessage, String userId) {
        AgentEntity agent = agents.findById(agentId)
                .orElseThrow(() -> new BusinessException(40401, "agent not found"));
        if (Boolean.FALSE.equals(agent.getEnabled())) {
            throw new BusinessException(40301, "agent disabled");
        }

        ConversationEntity conv = resolveConversation(agentId, conversationId, userId, userMessage);
        Long convId = conv.getId();
        saveMessage(convId, "user", userMessage, null);

        List<Message> baseMessages = buildMessages(agent, convId, userMessage);
        List<Long> toolIds = agentTools.findByAgentId(agent.getId()).stream()
                .map(AgentToolEntity::getToolId).toList();
        List<ToolCallback> toolCallbacks = toolCallbackFactory.build(toolIds);

        if ("react".equals(agent.getAgentType()) && !toolCallbacks.isEmpty()) {
            return reactChat(agent, convId, baseMessages, toolCallbacks);
        }
        return streamChat(agent, convId, baseMessages, toolCallbacks);
    }

    // ===== streaming path (chat / rag / tool, framework internal tool execution) =====

    private Flux<ChatChunk> streamChat(AgentEntity agent, Long convId,
                                       List<Message> baseMessages, List<ToolCallback> toolCallbacks) {
        OpenAiChatOptions.Builder ob = baseOptions(agent);
        if (!toolCallbacks.isEmpty()) {
            ob.toolCallbacks(toolCallbacks);
        }
        Prompt prompt = new Prompt(baseMessages, ob.build());

        StringBuilder full = new StringBuilder();
        Flux<ChatChunk> meta = Flux.just(new ChatChunk(convId, "meta", null));
        Flux<ChatChunk> tokens = chatModel.stream(prompt)
                .map(resp -> {
                    String text = resp.getResult().getOutput().getText();
                    return text != null ? text : "";
                })
                .filter(s -> !s.isEmpty())
                .doOnNext(full::append)
                .map(s -> new ChatChunk(convId, "token", s));
        Flux<ChatChunk> done = Flux.defer(() -> {
            saveMessage(convId, "assistant", full.toString(), null);
            return Flux.just(new ChatChunk(convId, "done", null));
        });
        return Flux.concat(meta, tokens, done);
    }

    // ===== react path (manual tool-calling loop, step events + trajectory) =====

    private Flux<ChatChunk> reactChat(AgentEntity agent, Long convId,
                                      List<Message> baseMessages, List<ToolCallback> toolCallbacks) {
        return Flux.defer(() -> {
            List<ChatChunk> out = new ArrayList<>();
            out.add(new ChatChunk(convId, "meta", null));
            List<Map<String, Object>> trajectory = new ArrayList<>();

            OpenAiChatOptions options = baseOptions(agent)
                    .toolCallbacks(toolCallbacks)
                    .internalToolExecutionEnabled(false)
                    .build();

            Prompt prompt = new Prompt(new ArrayList<>(baseMessages), options);
            ChatResponse response = chatModel.call(prompt);
            int steps = 0;

            while (response.hasToolCalls() && steps < MAX_STEPS) {
                List<AssistantMessage.ToolCall> calls = response.getResult().getOutput().getToolCalls();
                ToolExecutionResult execResult = toolCallingManager.executeToolCalls(prompt, response);
                Map<String, String> resultsById = extractToolResults(execResult);

                for (AssistantMessage.ToolCall call : calls) {
                    String result = resultsById.getOrDefault(call.id(), "");
                    String shortResult = result.length() > 300 ? result.substring(0, 300) : result;
                    out.add(new ChatChunk(convId, "step",
                            "调用 " + call.name() + "(" + call.arguments() + ") => " + shortResult));
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("tool", call.name());
                    entry.put("args", call.arguments());
                    entry.put("result", shortResult);
                    trajectory.add(entry);
                }

                prompt = new Prompt(execResult.conversationHistory(), options);
                response = chatModel.call(prompt);
                steps++;
            }

            String finalText = response.getResult().getOutput().getText();
            if (finalText == null) finalText = "";
            out.add(new ChatChunk(convId, "token", finalText));

            String trajectoryJson;
            try {
                trajectoryJson = objectMapper.writeValueAsString(trajectory);
            } catch (Exception e) {
                trajectoryJson = "[]";
            }
            saveMessage(convId, "assistant", finalText, trajectoryJson);

            out.add(new ChatChunk(convId, "done", null));
            return Flux.fromIterable(out);
        });
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

    // ===== shared helpers =====

    private OpenAiChatOptions.Builder baseOptions(AgentEntity agent) {
        return OpenAiChatOptions.builder()
                .model(agent.getModel())
                .temperature(agent.getTemperature())
                .maxTokens(agent.getMaxTokens())
                .topP(agent.getTopP());
    }

    private List<Message> buildMessages(AgentEntity agent, Long convId, String userMessage) {
        List<Message> msgs = new ArrayList<>();
        if (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
            msgs.add(new SystemMessage(agent.getSystemPrompt()));
        }
        for (MessageEntity m : messages.findByConversationIdOrderByCreatedAtAsc(convId)) {
            switch (m.getRole()) {
                case "user" -> msgs.add(new UserMessage(m.getContent()));
                case "assistant" -> msgs.add(new AssistantMessage(m.getContent()));
                case "system" -> msgs.add(new SystemMessage(m.getContent()));
                default -> { }
            }
        }
        List<Long> kbIds = bindings.findByAgentId(agent.getId()).stream()
                .map(AgentKnowledgeBaseEntity::getKbId).toList();
        if (!kbIds.isEmpty()) {
            List<RetrieveResult> hits = retriever.retrieve(kbIds, userMessage, 4);
            if (!hits.isEmpty()) {
                StringBuilder ctx = new StringBuilder("参考资料(请优先依据以下内容回答):\n");
                for (RetrieveResult h : hits) {
                    ctx.append("- ").append(h.content()).append("\n");
                }
                msgs.add(new SystemMessage(ctx.toString()));
            }
        }
        return msgs;
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
        c.setTitle(userMessage.length() > 20 ? userMessage.substring(0, 20) : userMessage);
        return conversations.save(c);
    }

    private void saveMessage(Long convId, String role, String content, String toolCallsJson) {
        MessageEntity m = new MessageEntity();
        m.setConversationId(convId);
        m.setRole(role);
        m.setContent(content);
        m.setToolCallsJson(toolCallsJson);
        messages.save(m);
    }
}
