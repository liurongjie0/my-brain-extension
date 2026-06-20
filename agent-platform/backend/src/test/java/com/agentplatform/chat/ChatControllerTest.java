package com.agentplatform.chat;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.agent.AgentEntity;
import com.agentplatform.agent.AgentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ChatControllerTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired AgentRepository agents;
    @MockitoBean ChatModel chatModel;

    private ChatResponse chunk(String t) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(t))));
    }

    @Test
    void sse_chat_streams_and_then_history_readable() throws Exception {
        AgentEntity a = new AgentEntity();
        a.setName("c"); a.setModel("gpt-4o-mini"); a.setSystemPrompt("sys");
        a.setTemperature(0.7); a.setMaxTokens(256); a.setTopP(1.0);
        a.setAgentType("chat"); a.setEnabled(true);
        Long agentId = agents.save(a).getId();

        Mockito.when(chatModel.stream(Mockito.any(Prompt.class)))
                .thenReturn(Flux.just(chunk("Hi"), chunk("!")));

        String body = om.writeValueAsString(new java.util.HashMap<>() {{
            put("agentId", agentId);
            put("message", "hello");
            put("userId", "u-1");
        }});

        var mvcResult = mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        String sse = mvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(sse).contains("Hi").contains("!");

        String convList = mvc.perform(get("/api/conversations").param("userId", "u-1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(convList).contains("\"code\":0");
    }
}
