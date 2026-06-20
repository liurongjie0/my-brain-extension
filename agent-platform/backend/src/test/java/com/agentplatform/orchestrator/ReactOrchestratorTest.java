package com.agentplatform.orchestrator;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.agent.AgentEntity;
import com.agentplatform.agent.AgentRepository;
import com.agentplatform.agent.AgentToolEntity;
import com.agentplatform.agent.AgentToolRepository;
import com.agentplatform.chat.MessageEntity;
import com.agentplatform.chat.MessageRepository;
import com.agentplatform.tool.ToolService;
import com.agentplatform.tool.dto.ToolRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReactOrchestratorTest extends IntegrationTestBase {
    @Autowired ChatOrchestrator orchestrator;
    @Autowired AgentRepository agents;
    @Autowired AgentToolRepository agentTools;
    @Autowired ToolService toolService;
    @Autowired MessageRepository messages;
    @MockitoBean ChatModel chatModel;

    HttpServer server;
    int port;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/calc", exchange -> {
            byte[] out = "{\"result\":2}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() { server.stop(0); }

    private ChatResponse toolCall() {
        AssistantMessage am = new AssistantMessage("", Map.of(),
                List.of(new AssistantMessage.ToolCall("call-1", "function", "calc", "{\"a\":1,\"b\":1}")));
        return new ChatResponse(List.of(new Generation(am)));
    }

    private ChatResponse finalAnswer() {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("结果是 2"))));
    }

    @Test
    void react_runs_tool_loop_emits_steps_and_persists_trajectory() {
        var tool = toolService.create(new ToolRequest("calc", "加法", "POST",
                "http://localhost:" + port + "/calc", null, "{\"type\":\"object\",\"properties\":{}}", true));

        AgentEntity a = new AgentEntity();
        a.setName("react"); a.setModel("gpt-4o-mini"); a.setSystemPrompt("sys");
        a.setTemperature(0.7); a.setMaxTokens(256); a.setTopP(1.0);
        a.setAgentType("react"); a.setEnabled(true);
        Long agentId = agents.save(a).getId();
        agentTools.save(new AgentToolEntity(agentId, tool.id()));

        Mockito.when(chatModel.call(Mockito.any(Prompt.class)))
                .thenReturn(toolCall(), finalAnswer());

        List<ChatChunk> chunks = orchestrator.chat(agentId, null, "1+1等于几", "u-1").collectList().block();

        assertThat(chunks).extracting(ChatChunk::type).contains("meta", "step", "token", "done");
        assertThat(chunks).anyMatch(c -> "step".equals(c.type()) && c.content() != null && c.content().contains("calc"));
        assertThat(chunks).anyMatch(c -> "token".equals(c.type()) && c.content() != null && c.content().contains("结果是 2"));

        Long convId = chunks.get(0).conversationId();
        List<MessageEntity> persisted = messages.findByConversationIdOrderByCreatedAtAsc(convId);
        MessageEntity assistant = persisted.stream().filter(m -> "assistant".equals(m.getRole())).reduce((x, y) -> y).orElseThrow();
        assertThat(assistant.getContent()).contains("结果是 2");
        assertThat(assistant.getToolCallsJson()).contains("calc");
    }
}
