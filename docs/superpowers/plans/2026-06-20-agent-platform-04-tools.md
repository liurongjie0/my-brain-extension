# Agent 平台 · 计划 04：工具（HTTP 工具 + Function Calling）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans / subagent-driven-development。Steps 用 `- [ ]`。

**Goal:** 管理员可配置 HTTP 接口型工具；Agent 绑定工具后，对话中模型可通过 Function Calling 自动调用工具（框架内置执行）。

**Architecture:** 新增 `tool` 包：工具 CRUD、`HttpToolExecutor`（按 method/url/headers 发请求）、`DynamicHttpToolCallback`（把工具定义适配成 Spring AI `ToolCallback`）、`ToolCallbackFactory`。`agent_tool` 绑定。`ChatOrchestrator` 在有绑定工具时把 `ToolCallback` 注入 `OpenAiChatOptions`，由框架自动执行。

**Tech Stack:** 沿用前序；HTTP 用 JDK `java.net.http.HttpClient`；JSON 用 Jackson（已随 web 引入）。

## Global Constraints

- JDK 17；`./mvnw` 前置 `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
- 包根 `com.agentplatform`；注释/命名英文，提交中文
- 集成测试继承 `IntegrationTestBase`
- Spring AI 1.0 `ToolCallback`/`ToolDefinition`/`OpenAiChatOptions.toolCallbacks` API 以编译为准微调

## 文件结构

```
backend/src/main/java/com/agentplatform/tool/
├── ToolEntity.java / ToolRepository.java
├── dto/ToolRequest.java / ToolResponse.java
├── ToolService.java                 # CRUD
├── HttpToolExecutor.java            # 执行 HTTP 调用
├── DynamicHttpToolCallback.java     # ToolEntity -> Spring AI ToolCallback
├── ToolCallbackFactory.java         # 按 toolIds 构建 callbacks
└── ToolController.java              # /api/admin/tools CRUD + /{id}/test
backend/src/main/java/com/agentplatform/agent/
├── AgentToolEntity.java / AgentToolId.java / AgentToolRepository.java
└── AgentController.java             # bindings 增加 toolIds
backend/src/main/java/com/agentplatform/orchestrator/ChatOrchestrator.java  # 注入工具 callbacks
```

---

### Task 1: 工具 CRUD（实体/仓库/服务/DTO/接口）

**Files:** `tool/ToolEntity.java`、`tool/ToolRepository.java`、`tool/dto/ToolRequest.java`、`tool/dto/ToolResponse.java`、`tool/ToolService.java`、`tool/ToolController.java`；Test: `tool/ToolServiceTest.java`

**Interfaces:**
- `ToolEntity`：`Long id, String name, String description, String method, String url, String headersJson, String paramsSchemaJson, Boolean enabled, LocalDateTime createdAt`。
- `ToolRepository`：`findAllById`、`findByEnabledTrue`。
- `ToolRequest(name, description, method, url, headersJson, paramsSchemaJson, enabled)`；`ToolResponse`（全字段 + id + createdAt，`from`）。
- `ToolService`：`create/listAll/get/update/delete`；`get` 缺失抛 `BusinessException(40405,"tool not found")`；`method` 默认 `POST`，`enabled` 默认 true。
- 接口：`/api/admin/tools` POST/GET/GET{id}/PUT{id}/DELETE{id}。

- [ ] **Step 1: 失败测试 ToolServiceTest.java**
```java
package com.agentplatform.tool;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.tool.dto.ToolRequest;
import com.agentplatform.tool.dto.ToolResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ToolServiceTest extends IntegrationTestBase {
    @Autowired ToolService service;

    @Test
    void create_applies_defaults() {
        ToolResponse r = service.create(new ToolRequest("天气查询", "查城市天气",
                null, "https://api.example.com/weather", null,
                "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}", null));
        assertThat(r.id()).isNotNull();
        assertThat(r.method()).isEqualTo("POST");
        assertThat(r.enabled()).isTrue();
    }
}
```
- [ ] **Step 2: 运行确认失败** `./mvnw -q -Dtest=ToolServiceTest test`
- [ ] **Step 3: ToolEntity.java**
```java
package com.agentplatform.tool;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tool")
public class ToolEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String description;
    private String method;
    private String url;
    @Column(name = "headers_json", columnDefinition = "TEXT")
    private String headersJson;
    @Column(name = "params_schema_json", columnDefinition = "TEXT")
    private String paramsSchemaJson;
    private Boolean enabled;
    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getHeadersJson() { return headersJson; }
    public void setHeadersJson(String headersJson) { this.headersJson = headersJson; }
    public String getParamsSchemaJson() { return paramsSchemaJson; }
    public void setParamsSchemaJson(String paramsSchemaJson) { this.paramsSchemaJson = paramsSchemaJson; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```
- [ ] **Step 4: ToolRepository.java**
```java
package com.agentplatform.tool;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ToolRepository extends JpaRepository<ToolEntity, Long> {
    List<ToolEntity> findByEnabledTrue();
}
```
- [ ] **Step 5: dto/ToolRequest.java 与 dto/ToolResponse.java**
```java
package com.agentplatform.tool.dto;

public record ToolRequest(String name, String description, String method, String url,
                          String headersJson, String paramsSchemaJson, Boolean enabled) {}
```
```java
package com.agentplatform.tool.dto;

import com.agentplatform.tool.ToolEntity;
import java.time.LocalDateTime;

public record ToolResponse(Long id, String name, String description, String method, String url,
                           String headersJson, String paramsSchemaJson, Boolean enabled,
                           LocalDateTime createdAt) {
    public static ToolResponse from(ToolEntity e) {
        return new ToolResponse(e.getId(), e.getName(), e.getDescription(), e.getMethod(),
                e.getUrl(), e.getHeadersJson(), e.getParamsSchemaJson(), e.getEnabled(), e.getCreatedAt());
    }
}
```
- [ ] **Step 6: ToolService.java**
```java
package com.agentplatform.tool;

import com.agentplatform.common.BusinessException;
import com.agentplatform.tool.dto.ToolRequest;
import com.agentplatform.tool.dto.ToolResponse;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ToolService {
    private final ToolRepository repository;
    public ToolService(ToolRepository repository) { this.repository = repository; }

    public ToolResponse create(ToolRequest req) {
        ToolEntity e = new ToolEntity();
        apply(e, req);
        return ToolResponse.from(repository.save(e));
    }
    public List<ToolResponse> listAll() {
        return repository.findAll().stream().map(ToolResponse::from).toList();
    }
    public ToolEntity getEntity(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(40405, "tool not found"));
    }
    public ToolResponse get(Long id) { return ToolResponse.from(getEntity(id)); }
    public ToolResponse update(Long id, ToolRequest req) {
        ToolEntity e = getEntity(id);
        apply(e, req);
        return ToolResponse.from(repository.save(e));
    }
    public void delete(Long id) { repository.delete(getEntity(id)); }

    private void apply(ToolEntity e, ToolRequest req) {
        e.setName(req.name());
        e.setDescription(req.description());
        e.setMethod(req.method() != null ? req.method() : "POST");
        e.setUrl(req.url());
        e.setHeadersJson(req.headersJson());
        e.setParamsSchemaJson(req.paramsSchemaJson());
        e.setEnabled(req.enabled() != null ? req.enabled() : true);
    }
}
```
- [ ] **Step 7: ToolController.java**（CRUD；`/{id}/test` 在 Task 3 加）
```java
package com.agentplatform.tool;

import com.agentplatform.common.ApiResponse;
import com.agentplatform.tool.dto.ToolRequest;
import com.agentplatform.tool.dto.ToolResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/tools")
public class ToolController {
    private final ToolService service;
    public ToolController(ToolService service) { this.service = service; }

    @PostMapping public ApiResponse<ToolResponse> create(@RequestBody ToolRequest req) { return ApiResponse.ok(service.create(req)); }
    @GetMapping public ApiResponse<List<ToolResponse>> listAll() { return ApiResponse.ok(service.listAll()); }
    @GetMapping("/{id}") public ApiResponse<ToolResponse> get(@PathVariable Long id) { return ApiResponse.ok(service.get(id)); }
    @PutMapping("/{id}") public ApiResponse<ToolResponse> update(@PathVariable Long id, @RequestBody ToolRequest req) { return ApiResponse.ok(service.update(id, req)); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.ok(null); }
}
```
- [ ] **Step 8: 运行确认通过；Step 9: 提交** `git commit -m "添加工具 CRUD"`

---

### Task 2: HttpToolExecutor（执行 HTTP 调用）

**Files:** `tool/HttpToolExecutor.java`；Test: `tool/HttpToolExecutorTest.java`（内嵌 `com.sun.net.httpserver.HttpServer`）

**Interfaces:**
- `HttpToolExecutor.execute(ToolEntity tool, String argsJson) -> String`：POST 时把 `argsJson` 作为请求体（`Content-Type: application/json`）；GET 时把 args 顶层键值拼成 query；附加 `headersJson` 解析出的请求头；返回响应体字符串（截断到 8KB）。异常返回 `{"error":"..."}`。

- [ ] **Step 1: 失败测试 HttpToolExecutorTest.java**
```java
package com.agentplatform.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HttpToolExecutorTest {

    HttpServer server;
    int port;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/echo", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String resp = "{\"received\":" + new String(body, StandardCharsets.UTF_8) + "}";
            byte[] out = resp.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() { server.stop(0); }

    @Test
    void posts_args_as_json_body() {
        HttpToolExecutor executor = new HttpToolExecutor(new ObjectMapper());
        ToolEntity tool = new ToolEntity();
        tool.setMethod("POST");
        tool.setUrl("http://localhost:" + port + "/echo");
        String result = executor.execute(tool, "{\"city\":\"上海\"}");
        assertThat(result).contains("received").contains("上海");
    }
}
```
- [ ] **Step 2: 运行确认失败**
- [ ] **Step 3: HttpToolExecutor.java**
```java
package com.agentplatform.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public class HttpToolExecutor {

    private final ObjectMapper objectMapper;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public HttpToolExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String execute(ToolEntity tool, String argsJson) {
        try {
            String method = tool.getMethod() != null ? tool.getMethod().toUpperCase() : "POST";
            String url = tool.getUrl();
            HttpRequest.Builder builder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(30));

            applyHeaders(builder, tool.getHeadersJson());

            if ("GET".equals(method)) {
                url = url + toQuery(argsJson);
                builder.uri(URI.create(url)).GET();
            } else {
                builder.uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(
                                argsJson != null ? argsJson : "{}", StandardCharsets.UTF_8));
            }

            HttpResponse<String> resp = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = resp.body();
            return body.length() > 8192 ? body.substring(0, 8192) : body;
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private void applyHeaders(HttpRequest.Builder builder, String headersJson) {
        if (headersJson == null || headersJson.isBlank()) return;
        try {
            Map<String, String> headers = objectMapper.readValue(headersJson, new TypeReference<>() {});
            headers.forEach(builder::header);
        } catch (Exception ignored) { }
    }

    private String toQuery(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) return "";
        try {
            Map<String, Object> args = objectMapper.readValue(argsJson, new TypeReference<>() {});
            if (args.isEmpty()) return "";
            StringBuilder sb = new StringBuilder("?");
            args.forEach((k, v) -> sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                    .append("=").append(URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8)).append("&"));
            return sb.substring(0, sb.length() - 1);
        } catch (Exception e) {
            return "";
        }
    }
}
```
- [ ] **Step 4: 运行确认通过；Step 5: 提交** `git commit -m "添加 HTTP 工具执行器"`

---

### Task 3: ToolCallback 适配 + 工具测试接口

**Files:** `tool/DynamicHttpToolCallback.java`、`tool/ToolCallbackFactory.java`；Modify `tool/ToolController.java`（加 `/{id}/test`）；Test: `tool/ToolCallbackFactoryTest.java`

**Interfaces:**
- `DynamicHttpToolCallback implements org.springframework.ai.tool.ToolCallback`：`getToolDefinition()` 返回 `ToolDefinition`（name=tool.name、description=tool.description、inputSchema=paramsSchemaJson 或 `{"type":"object","properties":{}}`）；`call(String toolInput)` 调 `HttpToolExecutor.execute`。
- `ToolCallbackFactory.build(List<Long> toolIds) -> List<ToolCallback>`（仅启用的）。
- 接口：`POST /api/admin/tools/{id}/test` body=`{"args": "{...json...}"}` → `ApiResponse<String>`（直接执行返回响应）。

- [ ] **Step 1: 失败测试 ToolCallbackFactoryTest.java**
```java
package com.agentplatform.tool;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.tool.dto.ToolRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallbackFactoryTest extends IntegrationTestBase {
    @Autowired ToolService toolService;
    @Autowired ToolCallbackFactory factory;

    @Test
    void builds_callback_with_definition() {
        var t = toolService.create(new ToolRequest("weather", "查天气", "POST",
                "http://localhost:9/none", null,
                "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}", true));
        List<ToolCallback> callbacks = factory.build(List.of(t.id()));
        assertThat(callbacks).hasSize(1);
        assertThat(callbacks.get(0).getToolDefinition().name()).isEqualTo("weather");
        assertThat(callbacks.get(0).getToolDefinition().description()).isEqualTo("查天气");
    }
}
```
- [ ] **Step 2: 运行确认失败**
- [ ] **Step 3: DynamicHttpToolCallback.java**
```java
package com.agentplatform.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

public class DynamicHttpToolCallback implements ToolCallback {

    private final ToolEntity tool;
    private final HttpToolExecutor executor;

    public DynamicHttpToolCallback(ToolEntity tool, HttpToolExecutor executor) {
        this.tool = tool;
        this.executor = executor;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        String schema = (tool.getParamsSchemaJson() != null && !tool.getParamsSchemaJson().isBlank())
                ? tool.getParamsSchemaJson() : "{\"type\":\"object\",\"properties\":{}}";
        return ToolDefinition.builder()
                .name(tool.getName())
                .description(tool.getDescription() != null ? tool.getDescription() : tool.getName())
                .inputSchema(schema)
                .build();
    }

    @Override
    public String call(String toolInput) {
        return executor.execute(tool, toolInput);
    }
}
```
- [ ] **Step 4: ToolCallbackFactory.java**
```java
package com.agentplatform.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ToolCallbackFactory {

    private final ToolRepository repository;
    private final HttpToolExecutor executor;

    public ToolCallbackFactory(ToolRepository repository, HttpToolExecutor executor) {
        this.repository = repository;
        this.executor = executor;
    }

    public List<ToolCallback> build(List<Long> toolIds) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (ToolEntity t : repository.findAllById(toolIds)) {
            if (Boolean.TRUE.equals(t.getEnabled())) {
                callbacks.add(new DynamicHttpToolCallback(t, executor));
            }
        }
        return callbacks;
    }
}
```
- [ ] **Step 5: ToolController 加测试接口**（注入 `HttpToolExecutor`）
```java
    // 构造器改为 ToolController(ToolService service, HttpToolExecutor executor)
    public record TestRequest(String args) {}

    @PostMapping("/{id}/test")
    public com.agentplatform.common.ApiResponse<String> test(@PathVariable Long id,
                                                             @RequestBody TestRequest req) {
        return com.agentplatform.common.ApiResponse.ok(executor.execute(service.getEntity(id), req.args()));
    }
```
- [ ] **Step 6: 运行确认通过；Step 7: 提交** `git commit -m "添加工具 ToolCallback 适配与测试接口"`

---

### Task 4: Agent-工具绑定 + 编排注入工具

**Files:** `agent/AgentToolId.java`、`agent/AgentToolEntity.java`、`agent/AgentToolRepository.java`；Modify `agent/AgentController.java`（bindings 加 toolIds）、`orchestrator/ChatOrchestrator.java`（注入工具 callbacks）；Test: `orchestrator/ToolBindingTest.java`

**Interfaces:**
- `AgentToolEntity`（`@IdClass(AgentToolId.class)`，`Long agentId, toolId`，表 `agent_tool`）。
- `AgentToolRepository`：`findByAgentId`、`deleteByAgentId`（`@Modifying @Transactional`）。
- `AgentController.BindingsRequest` 改为 `(List<Long> kbIds, List<Long> toolIds)`；bindings 同时重置 KB 与工具绑定。
- `ChatOrchestrator`：构造器追加 `AgentToolRepository agentTools`、`ToolCallbackFactory toolCallbackFactory`；`buildPrompt` 中若 agent 有绑定工具，则 `OpenAiChatOptions.builder().toolCallbacks(callbacks)`（框架内置自动执行）。

- [ ] **Step 1: 失败测试 ToolBindingTest.java**
```java
package com.agentplatform.orchestrator;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.agent.*;
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
```
- [ ] **Step 2: 运行确认失败**
- [ ] **Step 3: AgentToolId.java / AgentToolEntity.java / AgentToolRepository.java**（与 AgentKb 同构，字段 agentId/toolId，表 agent_tool）
```java
package com.agentplatform.agent;

import java.io.Serializable;
import java.util.Objects;

public class AgentToolId implements Serializable {
    private Long agentId;
    private Long toolId;
    public AgentToolId() {}
    public AgentToolId(Long agentId, Long toolId) { this.agentId = agentId; this.toolId = toolId; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentToolId that)) return false;
        return Objects.equals(agentId, that.agentId) && Objects.equals(toolId, that.toolId);
    }
    @Override public int hashCode() { return Objects.hash(agentId, toolId); }
}
```
```java
package com.agentplatform.agent;

import jakarta.persistence.*;

@Entity
@Table(name = "agent_tool")
@IdClass(AgentToolId.class)
public class AgentToolEntity {
    @Id @Column(name = "agent_id") private Long agentId;
    @Id @Column(name = "tool_id") private Long toolId;
    public AgentToolEntity() {}
    public AgentToolEntity(Long agentId, Long toolId) { this.agentId = agentId; this.toolId = toolId; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public Long getToolId() { return toolId; }
    public void setToolId(Long toolId) { this.toolId = toolId; }
}
```
```java
package com.agentplatform.agent;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import java.util.List;

public interface AgentToolRepository extends JpaRepository<AgentToolEntity, AgentToolId> {
    List<AgentToolEntity> findByAgentId(Long agentId);
    @Modifying @Transactional void deleteByAgentId(Long agentId);
}
```
- [ ] **Step 4: AgentController bindings 加 toolIds**（注入 `AgentToolRepository`，`BindingsRequest` 改 `(List<Long> kbIds, List<Long> toolIds)`，重置时同时处理 KB 与工具）
```java
    public record BindingsRequest(java.util.List<Long> kbIds, java.util.List<Long> toolIds) {}

    @PutMapping("/api/admin/agents/{id}/bindings")
    public ApiResponse<Void> bindings(@PathVariable Long id, @RequestBody BindingsRequest req) {
        service.get(id);
        agentKnowledgeBaseRepository.deleteByAgentId(id);
        if (req.kbIds() != null)
            for (Long kbId : req.kbIds()) agentKnowledgeBaseRepository.save(new AgentKnowledgeBaseEntity(id, kbId));
        agentToolRepository.deleteByAgentId(id);
        if (req.toolIds() != null)
            for (Long toolId : req.toolIds()) agentToolRepository.save(new AgentToolEntity(id, toolId));
        return ApiResponse.ok(null);
    }
```
- [ ] **Step 5: ChatOrchestrator 注入工具 callbacks**（构造器加 `AgentToolRepository agentTools, ToolCallbackFactory toolCallbackFactory`；`buildPrompt` 组装 options 时加工具）
```java
        List<Long> toolIds = agentTools.findByAgentId(agent.getId()).stream()
                .map(com.agentplatform.agent.AgentToolEntity::getToolId).toList();
        var toolCallbacks = toolCallbackFactory.build(toolIds);

        OpenAiChatOptions.Builder ob = OpenAiChatOptions.builder()
                .model(agent.getModel())
                .temperature(agent.getTemperature())
                .maxTokens(agent.getMaxTokens())
                .topP(agent.getTopP());
        if (!toolCallbacks.isEmpty()) {
            ob.toolCallbacks(toolCallbacks);
        }
        OpenAiChatOptions options = ob.build();
```
> 替换原先直接 `OpenAiChatOptions.builder()...build()` 的写法。
- [ ] **Step 6: 运行确认通过** `./mvnw -q -Dtest=ToolBindingTest test`
- [ ] **Step 7: 全量测试** `./mvnw test`
- [ ] **Step 8: 提交** `git commit -m "添加 Agent-工具绑定与编排工具注入"`

---

## Self-Review
- 工具 CRUD（设计 admin tools）✅；HTTP 执行器 ✅；ToolCallback 适配 + 测试接口 ✅；Agent 绑定工具 + 编排注入（框架内置执行）✅。
- 多步骤可视化/轨迹落库留待计划 05（手动循环）。
- 不打真实网络：HttpToolExecutor 用内嵌 server 测；编排用捕获 Prompt 断言 ✅。
- 风险：`ToolDefinition.builder().inputSchema(...)`、`OpenAiChatOptions.builder().toolCallbacks(...)`、`getToolCallbacks()` 的 1.0 签名以编译为准微调。
