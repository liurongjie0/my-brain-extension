package com.agentplatform.orchestrator;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.agent.AgentEntity;
import com.agentplatform.agent.AgentKnowledgeBaseEntity;
import com.agentplatform.agent.AgentKnowledgeBaseRepository;
import com.agentplatform.agent.AgentRepository;
import com.agentplatform.rag.DocumentProcessingService;
import com.agentplatform.rag.DocumentService;
import com.agentplatform.rag.KnowledgeBaseService;
import com.agentplatform.rag.dto.KnowledgeBaseRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagAugmentationTest extends IntegrationTestBase {

    @Autowired ChatOrchestrator orchestrator;
    @Autowired AgentRepository agents;
    @Autowired AgentKnowledgeBaseRepository bindings;
    @Autowired KnowledgeBaseService kbService;
    @Autowired DocumentService documentService;
    @Autowired DocumentProcessingService processing;
    @MockitoBean ChatModel chatModel;

    @Test
    void bound_kb_content_is_injected_into_prompt() {
        Long kbId = kbService.create(new KnowledgeBaseRequest("kb", null, null, null, null)).id();
        var doc = documentService.upload(kbId, "s.txt", "txt", "公司年假政策: SECRET_POLICY_42 天。".repeat(10));
        processing.process(doc.id());

        AgentEntity a = new AgentEntity();
        a.setName("hr"); a.setModel("gpt-4o-mini"); a.setSystemPrompt("你是HR助手");
        a.setTemperature(0.7); a.setMaxTokens(256); a.setTopP(1.0);
        a.setAgentType("rag"); a.setEnabled(true);
        Long agentId = agents.save(a).getId();
        bindings.save(new AgentKnowledgeBaseEntity(agentId, kbId));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        Mockito.when(chatModel.stream(captor.capture()))
                .thenReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))))));

        orchestrator.chat(agentId, null, "年假多少天", "u-1").collectList().block();

        String promptText = captor.getValue().getInstructions().stream()
                .map(Message::getText).reduce("", (x, y) -> x + "\n" + y);
        assertThat(promptText).contains("SECRET_POLICY_42");
    }
}
