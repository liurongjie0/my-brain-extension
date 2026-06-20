package com.agentplatform.orchestrator;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.agent.AgentEntity;
import com.agentplatform.agent.AgentRepository;
import com.agentplatform.agent.AgentToolEntity;
import com.agentplatform.agent.AgentToolRepository;
import com.agentplatform.tool.ToolService;
import com.agentplatform.tool.dto.ToolRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolBindingTest extends IntegrationTestBase {
    @Autowired ChatOrchestrator orchestrator;
    @Autowired AgentRepository agents;
    @Autowired AgentToolRepository agentTools;
    @Autowired ToolService toolService;
    @MockitoBean ChatModel chatModel;

    @Test
    void bound_tools_are_registered_as_callbacks_in_prompt() {
        var tool = toolService.create(new ToolRequest("calc", "做加法", "POST",
                "http://localhost:9/none", null, "{\"type\":\"object\",\"properties\":{}}", true));

        AgentEntity a = new AgentEntity();
        a.setName("tool-agent"); a.setModel("gpt-4o-mini"); a.setSystemPrompt("sys");
        a.setTemperature(0.7); a.setMaxTokens(256); a.setTopP(1.0);
        a.setAgentType("tool"); a.setEnabled(true);
        Long agentId = agents.save(a).getId();
        agentTools.save(new AgentToolEntity(agentId, tool.id()));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        Mockito.when(chatModel.stream(captor.capture()))
                .thenReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))))));

        orchestrator.chat(agentId, null, "1+1", "u-1").collectList().block();

        OpenAiChatOptions opts = (OpenAiChatOptions) captor.getValue().getOptions();
        assertThat(opts.getToolCallbacks()).extracting(c -> c.getToolDefinition().name()).contains("calc");
    }
}
