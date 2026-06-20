# Agent 平台 · 计划 05：多步骤 ReAct 编排 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans。Steps 用 `- [ ]`。

**Goal:** 为 `react` 型且绑定了工具的 Agent 提供多步骤推理：手动工具执行循环（maxSteps 上限），把每一步（思考/工具调用/结果）通过 SSE 推给前端，并把完整轨迹落库到 `message.tool_calls_json`。

**Architecture:** `ChatOrchestrator` 拆出共享的 `buildMessages`（system + 历史 + RAG）；新增 `reactChat` 路径：用 `OpenAiChatOptions(internalToolExecutionEnabled=false)` + `ToolCallingManager` 手动循环。非 react 路径维持计划 02/04 的流式 + 框架内置执行。

**Tech Stack:** 沿用；用 Spring AI `ToolCallingManager`、`ToolExecutionResult`、`AssistantMessage.ToolCall`、`ToolResponseMessage`。

## Global Constraints
- JDK 17；`./mvnw` 前置 `JAVA_HOME=...openjdk@17...`；包根 `com.agentplatform`
- 集成测试继承 `IntegrationTestBase`；模型用 `@MockitoBean`，工具指向内嵌 HttpServer
- Spring AI 1.0 手动工具执行 API 以编译为准微调

## 文件结构
```
backend/src/main/java/com/agentplatform/orchestrator/ChatOrchestrator.java   # 重构 + reactChat
backend/src/test/java/com/agentplatform/orchestrator/ReactOrchestratorTest.java
```

---

### Task 1: 重构 ChatOrchestrator 抽出 buildMessages，新增 react 路径

**Files:** Modify `orchestrator/ChatOrchestrator.java`；Test: `orchestrator/ReactOrchestratorTest.java`

**Interfaces:**
- 构造器追加注入 `org.springframework.ai.model.tool.ToolCallingManager toolCallingManager` 与 `com.fasterxml.jackson.databind.ObjectMapper objectMapper`。
- `chat(...)`：保存用户消息后，若 `"react".equals(agent.getAgentType())` 且该 agent 有绑定工具，走 `reactChat`；否则走原 `streamChat`（原流式逻辑改名）。
- `reactChat`：手动循环（≤ `MAX_STEPS=8`）；emit `meta` → 每步 `step`（content 为 `调用 <tool>(<args>) => <result截断>`）→ 最终 `token`(完整回答) → `done`；落库 assistant 消息（content=最终回答，tool_calls_json=轨迹 JSON 数组）。
- 行为：`ChatChunk` 复用（type 增加 `step`）。

- [ ] **Step 1: 失败测试 ReactOrchestratorTest.java**

```java
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

        // 第一次返回工具调用, 第二次返回最终答案
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
```

- [ ] **Step 2: 运行确认失败** `./mvnw -q -Dtest=ReactOrchestratorTest test`

- [ ] **Step 3: 重构 ChatOrchestrator.java（最终内容）**

```java
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
import java.util.List;
import java.util.Map;

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
                    Map<String, Object> entry = new java.util.LinkedHashMap<>();
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
        Map<String, String> byId = new java.util.HashMap<>();
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
```

> 说明：原 `saveMessage(convId, role, content)` 调用处全部改为四参 `saveMessage(convId, role, content, toolCallsJson)`（流式路径传 `null`）。原 `buildPrompt` 删除，逻辑拆入 `buildMessages` + `baseOptions`。

- [ ] **Step 4: 运行确认通过** `./mvnw -q -Dtest=ReactOrchestratorTest test`
- [ ] **Step 5: 全量测试** `./mvnw test`（确认既有流式/工具/RAG 测试不回归）
- [ ] **Step 6: 提交** `git commit -m "添加多步骤 ReAct 编排: 手动工具循环/步骤事件/轨迹落库"`

---

## Self-Review
- 多步骤循环 + maxSteps（设计第四节 ReAct）✅；步骤事件 SSE（step 类型）✅；轨迹落库 tool_calls_json ✅；RAG+工具叠加（buildMessages 含 RAG，react 路径带工具）✅。
- 非 react 路径维持流式 + 框架内置执行（计划 02/04）不回归 ✅。
- 不打真实网络：ChatModel mock 返回工具调用/最终答案，工具指向内嵌 server ✅。
- 风险：`ToolCallingManager`/`ToolExecutionResult.conversationHistory()`/`ToolResponseMessage.ToolResponse.id()/responseData()`/`response.hasToolCalls()`/`AssistantMessage.ToolCall(id,type,name,arguments)`/`internalToolExecutionEnabled` 的 1.0 签名以编译为准微调。
- 偏离：路线图原写"引入 Spring AI Alibaba graph"；实际用 Spring AI 核心的 `ToolCallingManager` 手动循环，更可控可测，等价达成多步骤目标（Spring AI Alibaba 构建于 Spring AI 核心之上）。
