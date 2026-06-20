package com.agentplatform.orchestrator;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.agent.AgentEntity;
import com.agentplatform.agent.AgentRepository;
import com.agentplatform.chat.MessageEntity;
import com.agentplatform.chat.MessageRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatOrchestratorTest extends IntegrationTestBase {

    @Autowired ChatOrchestrator orchestrator;
    @Autowired AgentRepository agents;
    @Autowired MessageRepository messages;

    @MockitoBean ChatModel chatModel;

    private ChatResponse chunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void streams_tokens_and_persists_messages() {
        AgentEntity a = new AgentEntity();
        a.setName("t"); a.setModel("gpt-4o-mini"); a.setSystemPrompt("sys");
        a.setTemperature(0.7); a.setMaxTokens(256); a.setTopP(1.0);
        a.setAgentType("chat"); a.setEnabled(true);
        Long agentId = agents.save(a).getId();

        Mockito.when(chatModel.stream(Mockito.any(Prompt.class)))
                .thenReturn(Flux.just(chunk("你好"), chunk("，世界")));

        Flux<ChatChunk> flux = orchestrator.chat(agentId, null, "hi", "u-1");

        ChatChunk first = flux.blockFirst();
        assertThat(first.type()).isEqualTo("meta");
        assertThat(first.conversationId()).isNotNull();

        Mockito.when(chatModel.stream(Mockito.any(Prompt.class)))
                .thenReturn(Flux.just(chunk("A"), chunk("B")));
        Flux<ChatChunk> flux2 = orchestrator.chat(agentId, first.conversationId(), "again", "u-1");
        List<ChatChunk> all = flux2.collectList().block();
        assertThat(all).extracting(ChatChunk::type).contains("token", "done");
        String joined = all.stream().filter(c -> c.type().equals("token"))
                .map(ChatChunk::content).reduce("", String::concat);
        assertThat(joined).isEqualTo("AB");

        List<MessageEntity> persisted = messages.findByConversationIdOrderByCreatedAtAsc(first.conversationId());
        assertThat(persisted).extracting(MessageEntity::getRole)
                .containsSubsequence("user", "assistant");
        assertThat(persisted).anyMatch(m -> "AB".equals(m.getContent()));
    }
}
