package com.agentplatform.orchestrator;

import com.agentplatform.agent.AgentEntity;
import com.agentplatform.agent.AgentRepository;
import com.agentplatform.chat.ConversationEntity;
import com.agentplatform.chat.ConversationRepository;
import com.agentplatform.chat.MessageEntity;
import com.agentplatform.chat.MessageRepository;
import com.agentplatform.common.BusinessException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-chat orchestration: assembles the prompt from the agent's system prompt and
 * conversation history, streams the model response over SSE-friendly chunks, and
 * persists the user/assistant messages. RAG and tools join in later plans.
 */
@Service
public class ChatOrchestrator {

    private final ChatModel chatModel;
    private final AgentRepository agents;
    private final ConversationRepository conversations;
    private final MessageRepository messages;

    public ChatOrchestrator(ChatModel chatModel, AgentRepository agents,
                            ConversationRepository conversations, MessageRepository messages) {
        this.chatModel = chatModel;
        this.agents = agents;
        this.conversations = conversations;
        this.messages = messages;
    }

    public Flux<ChatChunk> chat(Long agentId, Long conversationId, String userMessage, String userId) {
        AgentEntity agent = agents.findById(agentId)
                .orElseThrow(() -> new BusinessException(40401, "agent not found"));
        if (Boolean.FALSE.equals(agent.getEnabled())) {
            throw new BusinessException(40301, "agent disabled");
        }

        ConversationEntity conv = resolveConversation(agentId, conversationId, userId, userMessage);
        Long convId = conv.getId();

        // persist the user message first so history is consistent
        saveMessage(convId, "user", userMessage);

        Prompt prompt = buildPrompt(agent, convId);

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
            saveMessage(convId, "assistant", full.toString());
            return Flux.just(new ChatChunk(convId, "done", null));
        });

        return Flux.concat(meta, tokens, done);
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

    private Prompt buildPrompt(AgentEntity agent, Long convId) {
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
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(agent.getModel())
                .temperature(agent.getTemperature())
                .maxTokens(agent.getMaxTokens())
                .topP(agent.getTopP())
                .build();
        return new Prompt(msgs, options);
    }

    private void saveMessage(Long convId, String role, String content) {
        MessageEntity m = new MessageEntity();
        m.setConversationId(convId);
        m.setRole(role);
        m.setContent(content);
        messages.save(m);
    }
}
