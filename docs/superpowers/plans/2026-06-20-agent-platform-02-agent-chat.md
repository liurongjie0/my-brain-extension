# Agent 平台 · 计划 02：Agent 配置 + 纯对话 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让平台能创建/配置 Agent，并与一个 Agent 进行纯对话（OpenAI 兼容、SSE 流式），会话与消息持久化到 MySQL。

**Architecture:** 在计划 01 的模块化单体上新增 `agent`（配置 CRUD）、`chat`（会话/消息持久化 + SSE）、`orchestrator`（编排骨架，本计划只实现 chat 分支）三个包。模型调用走 Spring AI 的 `ChatModel`（OpenAI 兼容），测试中用 `@MockBean` 替换，不打真实网络。

**Tech Stack:** 沿用计划 01（Spring Boot 3.4.1 / Spring AI 1.0.0 / JPA / MySQL / Flyway / Testcontainers 2.0.5）。SSE 用 Spring MVC 返回 `Flux<ServerSentEvent<String>>`（reactor 由 spring-ai 引入）。

## Global Constraints

- JDK 17；所有 `./mvnw` 命令前置 `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
- 包根 `com.agentplatform`；代码注释/命名英文，提交信息中文
- 模型调用测试中一律 `@MockBean ChatModel`，不打真实网络
- 集成测试继承计划 01 的 `IntegrationTestBase`（单例 MySQL 容器）
- **本计划把 `application.yml` 的 `spring.jpa.hibernate.ddl-auto` 由 `validate` 改为 `none`**：Flyway 为 schema 唯一权威，实体映射正确性由"存取往返集成测试"保证

## 文件结构（本计划新增/修改）

```
backend/src/main/java/com/agentplatform/
├── agent/
│   ├── AgentEntity.java            # JPA 实体 -> agent 表
│   ├── AgentRepository.java        # JpaRepository
│   ├── AgentService.java           # CRUD + 启用过滤
│   ├── AgentController.java        # /api/admin/agents + /api/agents
│   └── dto/
│       ├── AgentRequest.java       # 创建/更新入参
│       └── AgentResponse.java      # 出参
├── chat/
│   ├── ConversationEntity.java     # -> conversation 表
│   ├── MessageEntity.java          # -> message 表
│   ├── ConversationRepository.java
│   ├── MessageRepository.java
│   ├── ChatController.java         # POST /api/chat (SSE) + 会话查询
│   └── dto/
│       ├── ChatRequest.java
│       ├── ConversationResponse.java
│       └── MessageResponse.java
└── orchestrator/
    └── ChatOrchestrator.java       # 纯对话编排: 组装 prompt -> 流式 -> 落库

backend/src/main/resources/application.yml   # 修改: ddl-auto -> none
```

---

### Task 1: Agent 实体与 Repository

**Files:**
- Create: `backend/src/main/java/com/agentplatform/agent/AgentEntity.java`
- Create: `backend/src/main/java/com/agentplatform/agent/AgentRepository.java`
- Modify: `backend/src/main/resources/application.yml`（`ddl-auto: validate` → `none`）
- Test: `backend/src/test/java/com/agentplatform/agent/AgentRepositoryTest.java`

**Interfaces:**
- Produces:
  - `AgentEntity`：字段 `Long id, String name, String description, String avatar, String systemPrompt, String model, Double temperature, Integer maxTokens, Double topP, String agentType, Boolean enabled, LocalDateTime createdAt, LocalDateTime updatedAt`，getter/setter 齐全。
  - `AgentRepository extends JpaRepository<AgentEntity, Long>`，含 `List<AgentEntity> findByEnabledTrue()`。

- [ ] **Step 1: 改 application.yml 的 ddl-auto**

把
```yaml
  jpa:
    hibernate:
      ddl-auto: validate
```
改为
```yaml
  jpa:
    hibernate:
      ddl-auto: none
```

- [ ] **Step 2: 写失败测试 AgentRepositoryTest.java**

```java
package com.agentplatform.agent;

import com.agentplatform.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRepositoryTest extends IntegrationTestBase {

    @Autowired
    AgentRepository repository;

    @Test
    void saves_and_finds_enabled_agents() {
        AgentEntity a = new AgentEntity();
        a.setName("客服助手");
        a.setModel("gpt-4o-mini");
        a.setSystemPrompt("你是客服");
        a.setTemperature(0.5);
        a.setMaxTokens(1024);
        a.setTopP(1.0);
        a.setAgentType("chat");
        a.setEnabled(true);
        AgentEntity saved = repository.save(a);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        AgentEntity disabled = new AgentEntity();
        disabled.setName("停用的");
        disabled.setModel("gpt-4o-mini");
        disabled.setAgentType("chat");
        disabled.setEnabled(false);
        repository.save(disabled);

        List<AgentEntity> enabled = repository.findByEnabledTrue();
        assertThat(enabled).extracting(AgentEntity::getName).contains("客服助手").doesNotContain("停用的");
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=AgentRepositoryTest test`
Expected: 编译失败（`AgentEntity`/`AgentRepository` 不存在）。

- [ ] **Step 4: 写 AgentEntity.java**

```java
package com.agentplatform.agent;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent")
public class AgentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private String avatar;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    private String model;
    private Double temperature;
    private Integer maxTokens;
    private Double topP;
    private String agentType;
    private Boolean enabled;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public Double getTopP() { return topP; }
    public void setTopP(Double topP) { this.topP = topP; }
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 5: 写 AgentRepository.java**

```java
package com.agentplatform.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRepository extends JpaRepository<AgentEntity, Long> {
    List<AgentEntity> findByEnabledTrue();
}
```

- [ ] **Step 6: 运行测试确认通过**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=AgentRepositoryTest test`
Expected: PASS。

- [ ] **Step 7: 提交**

```bash
cd /Users/liurongjie/my-brain-extension
git add agent-platform/backend/src/main/java/com/agentplatform/agent agent-platform/backend/src/main/resources/application.yml agent-platform/backend/src/test/java/com/agentplatform/agent
git commit -m "添加 Agent 实体与仓库; ddl-auto 改为 none(Flyway 权威)"
```

---

### Task 2: Agent CRUD Service + DTO

**Files:**
- Create: `backend/src/main/java/com/agentplatform/agent/dto/AgentRequest.java`
- Create: `backend/src/main/java/com/agentplatform/agent/dto/AgentResponse.java`
- Create: `backend/src/main/java/com/agentplatform/agent/AgentService.java`
- Test: `backend/src/test/java/com/agentplatform/agent/AgentServiceTest.java`

**Interfaces:**
- Consumes: `AgentRepository`、`AgentEntity`（Task 1）、`BusinessException`（计划 01 common）。
- Produces:
  - `AgentRequest`：`String name, description, avatar, systemPrompt, model, agentType; Double temperature, topP; Integer maxTokens; Boolean enabled`（record）。
  - `AgentResponse`：`Long id` + 上述全部 + `LocalDateTime createdAt, updatedAt`（record），静态 `from(AgentEntity)`。
  - `AgentService`：`AgentResponse create(AgentRequest)`、`List<AgentResponse> listAll()`、`List<AgentResponse> listEnabled()`、`AgentResponse get(Long)`、`AgentResponse update(Long, AgentRequest)`、`void delete(Long)`。`get/update/delete` 找不到时抛 `BusinessException(40401, "agent not found")`。create/update 对 `temperature/maxTokens/topP/agentType/enabled` 应用默认值（0.7 / 2048 / 1.0 / "chat" / true）。

- [ ] **Step 1: 写失败测试 AgentServiceTest.java**

```java
package com.agentplatform.agent;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.agent.dto.AgentRequest;
import com.agentplatform.agent.dto.AgentResponse;
import com.agentplatform.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentServiceTest extends IntegrationTestBase {

    @Autowired
    AgentService service;

    @Test
    void create_applies_defaults() {
        AgentRequest req = new AgentRequest("助手", null, null, "你好", "gpt-4o-mini",
                null, null, null, null, null);
        AgentResponse r = service.create(req);

        assertThat(r.id()).isNotNull();
        assertThat(r.temperature()).isEqualTo(0.7);
        assertThat(r.maxTokens()).isEqualTo(2048);
        assertThat(r.topP()).isEqualTo(1.0);
        assertThat(r.agentType()).isEqualTo("chat");
        assertThat(r.enabled()).isTrue();
    }

    @Test
    void update_changes_fields() {
        AgentResponse created = service.create(new AgentRequest("a", null, null, null,
                "gpt-4o-mini", null, null, null, null, null));
        AgentResponse updated = service.update(created.id(), new AgentRequest("b", "desc", null,
                "sys", "gpt-4o", 0.2, 0.9, 512, "rag", false));

        assertThat(updated.name()).isEqualTo("b");
        assertThat(updated.model()).isEqualTo("gpt-4o");
        assertThat(updated.agentType()).isEqualTo("rag");
        assertThat(updated.enabled()).isFalse();
    }

    @Test
    void get_missing_throws() {
        assertThatThrownBy(() -> service.get(999999L))
                .isInstanceOf(BusinessException.class);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=AgentServiceTest test`
Expected: 编译失败（类不存在）。

- [ ] **Step 3: 写 AgentRequest.java**

```java
package com.agentplatform.agent.dto;

public record AgentRequest(
        String name,
        String description,
        String avatar,
        String systemPrompt,
        String model,
        Double temperature,
        Double topP,
        Integer maxTokens,
        String agentType,
        Boolean enabled
) {}
```

- [ ] **Step 4: 写 AgentResponse.java**

```java
package com.agentplatform.agent.dto;

import com.agentplatform.agent.AgentEntity;

import java.time.LocalDateTime;

public record AgentResponse(
        Long id,
        String name,
        String description,
        String avatar,
        String systemPrompt,
        String model,
        Double temperature,
        Double topP,
        Integer maxTokens,
        String agentType,
        Boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AgentResponse from(AgentEntity e) {
        return new AgentResponse(
                e.getId(), e.getName(), e.getDescription(), e.getAvatar(),
                e.getSystemPrompt(), e.getModel(), e.getTemperature(), e.getTopP(),
                e.getMaxTokens(), e.getAgentType(), e.getEnabled(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
```

- [ ] **Step 5: 写 AgentService.java**

```java
package com.agentplatform.agent;

import com.agentplatform.agent.dto.AgentRequest;
import com.agentplatform.agent.dto.AgentResponse;
import com.agentplatform.common.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentService {

    private final AgentRepository repository;

    public AgentService(AgentRepository repository) {
        this.repository = repository;
    }

    public AgentResponse create(AgentRequest req) {
        AgentEntity e = new AgentEntity();
        apply(e, req);
        return AgentResponse.from(repository.save(e));
    }

    public List<AgentResponse> listAll() {
        return repository.findAll().stream().map(AgentResponse::from).toList();
    }

    public List<AgentResponse> listEnabled() {
        return repository.findByEnabledTrue().stream().map(AgentResponse::from).toList();
    }

    public AgentResponse get(Long id) {
        return AgentResponse.from(find(id));
    }

    public AgentResponse update(Long id, AgentRequest req) {
        AgentEntity e = find(id);
        apply(e, req);
        return AgentResponse.from(repository.save(e));
    }

    public void delete(Long id) {
        repository.delete(find(id));
    }

    private AgentEntity find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(40401, "agent not found"));
    }

    private void apply(AgentEntity e, AgentRequest req) {
        e.setName(req.name());
        e.setDescription(req.description());
        e.setAvatar(req.avatar());
        e.setSystemPrompt(req.systemPrompt());
        e.setModel(req.model());
        e.setTemperature(req.temperature() != null ? req.temperature() : 0.7);
        e.setTopP(req.topP() != null ? req.topP() : 1.0);
        e.setMaxTokens(req.maxTokens() != null ? req.maxTokens() : 2048);
        e.setAgentType(req.agentType() != null ? req.agentType() : "chat");
        e.setEnabled(req.enabled() != null ? req.enabled() : true);
    }
}
```

- [ ] **Step 6: 运行测试确认通过**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=AgentServiceTest test`
Expected: PASS。

- [ ] **Step 7: 提交**

```bash
cd /Users/liurongjie/my-brain-extension
git add agent-platform/backend/src/main/java/com/agentplatform/agent agent-platform/backend/src/test/java/com/agentplatform/agent
git commit -m "添加 Agent CRUD service 与 DTO"
```

---

### Task 3: Agent Controller（管理后台 CRUD + 用户端列表）

**Files:**
- Create: `backend/src/main/java/com/agentplatform/agent/AgentController.java`
- Test: `backend/src/test/java/com/agentplatform/agent/AgentControllerTest.java`

**Interfaces:**
- Consumes: `AgentService`、`ApiResponse`（计划 01）。
- Produces: HTTP 接口
  - `POST /api/admin/agents` body=AgentRequest → `ApiResponse<AgentResponse>`
  - `GET /api/admin/agents` → `ApiResponse<List<AgentResponse>>`（全部）
  - `GET /api/admin/agents/{id}` → `ApiResponse<AgentResponse>`
  - `PUT /api/admin/agents/{id}` body=AgentRequest → `ApiResponse<AgentResponse>`
  - `DELETE /api/admin/agents/{id}` → `ApiResponse<Void>`
  - `GET /api/agents` → `ApiResponse<List<AgentResponse>>`（仅启用，用户端）

- [ ] **Step 1: 写失败测试 AgentControllerTest.java**

```java
package com.agentplatform.agent;

import com.agentplatform.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AgentControllerTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void create_then_list_and_public_filters_disabled() throws Exception {
        String body = om.writeValueAsString(new java.util.HashMap<>() {{
            put("name", "公开助手");
            put("model", "gpt-4o-mini");
            put("enabled", true);
        }});
        mvc.perform(post("/api/admin/agents").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0))
           .andExpect(jsonPath("$.data.id").isNumber())
           .andExpect(jsonPath("$.data.name").value("公开助手"));

        mvc.perform(get("/api/agents"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data[?(@.name=='公开助手')]").exists());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=AgentControllerTest test`
Expected: FAIL（无对应路由，404）。

- [ ] **Step 3: 写 AgentController.java**

```java
package com.agentplatform.agent;

import com.agentplatform.agent.dto.AgentRequest;
import com.agentplatform.agent.dto.AgentResponse;
import com.agentplatform.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class AgentController {

    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/agents")
    public ApiResponse<AgentResponse> create(@RequestBody AgentRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @GetMapping("/api/admin/agents")
    public ApiResponse<List<AgentResponse>> listAll() {
        return ApiResponse.ok(service.listAll());
    }

    @GetMapping("/api/admin/agents/{id}")
    public ApiResponse<AgentResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PutMapping("/api/admin/agents/{id}")
    public ApiResponse<AgentResponse> update(@PathVariable Long id, @RequestBody AgentRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/api/admin/agents/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/agents")
    public ApiResponse<List<AgentResponse>> listEnabled() {
        return ApiResponse.ok(service.listEnabled());
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=AgentControllerTest test`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
cd /Users/liurongjie/my-brain-extension
git add agent-platform/backend/src/main/java/com/agentplatform/agent/AgentController.java agent-platform/backend/src/test/java/com/agentplatform/agent/AgentControllerTest.java
git commit -m "添加 Agent 管理后台 CRUD 与用户端列表接口"
```

---

### Task 4: 会话与消息 实体 + Repository

**Files:**
- Create: `backend/src/main/java/com/agentplatform/chat/ConversationEntity.java`
- Create: `backend/src/main/java/com/agentplatform/chat/MessageEntity.java`
- Create: `backend/src/main/java/com/agentplatform/chat/ConversationRepository.java`
- Create: `backend/src/main/java/com/agentplatform/chat/MessageRepository.java`
- Test: `backend/src/test/java/com/agentplatform/chat/ChatPersistenceTest.java`

**Interfaces:**
- Produces:
  - `ConversationEntity`：`Long id, Long agentId, String userId, String title, LocalDateTime createdAt, updatedAt`。
  - `MessageEntity`：`Long id, Long conversationId, String role, String content, String toolCallsJson, Integer tokenUsage, LocalDateTime createdAt`。
  - `ConversationRepository extends JpaRepository<ConversationEntity, Long>`，含 `List<ConversationEntity> findByUserIdOrderByUpdatedAtDesc(String userId)`。
  - `MessageRepository extends JpaRepository<MessageEntity, Long>`，含 `List<MessageEntity> findByConversationIdOrderByCreatedAtAsc(Long conversationId)`。

- [ ] **Step 1: 写失败测试 ChatPersistenceTest.java**

```java
package com.agentplatform.chat;

import com.agentplatform.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPersistenceTest extends IntegrationTestBase {

    @Autowired ConversationRepository conversations;
    @Autowired MessageRepository messages;

    @Test
    void persists_conversation_and_ordered_messages() {
        ConversationEntity c = new ConversationEntity();
        c.setAgentId(1L);
        c.setUserId("u-1");
        c.setTitle("第一次对话");
        ConversationEntity saved = conversations.save(c);
        assertThat(saved.getId()).isNotNull();

        MessageEntity m1 = new MessageEntity();
        m1.setConversationId(saved.getId());
        m1.setRole("user");
        m1.setContent("你好");
        messages.save(m1);

        MessageEntity m2 = new MessageEntity();
        m2.setConversationId(saved.getId());
        m2.setRole("assistant");
        m2.setContent("你好，有什么可以帮你");
        messages.save(m2);

        List<MessageEntity> ordered = messages.findByConversationIdOrderByCreatedAtAsc(saved.getId());
        assertThat(ordered).extracting(MessageEntity::getRole).containsExactly("user", "assistant");

        List<ConversationEntity> mine = conversations.findByUserIdOrderByUpdatedAtDesc("u-1");
        assertThat(mine).extracting(ConversationEntity::getId).contains(saved.getId());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=ChatPersistenceTest test`
Expected: 编译失败（类不存在）。

- [ ] **Step 3: 写 ConversationEntity.java**

```java
package com.agentplatform.chat;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversation")
public class ConversationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id")
    private Long agentId;

    @Column(name = "user_id")
    private String userId;

    private String title;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 4: 写 MessageEntity.java**

```java
package com.agentplatform.chat;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "message")
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id")
    private Long conversationId;

    private String role;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "tool_calls_json", columnDefinition = "MEDIUMTEXT")
    private String toolCallsJson;

    @Column(name = "token_usage")
    private Integer tokenUsage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getToolCallsJson() { return toolCallsJson; }
    public void setToolCallsJson(String toolCallsJson) { this.toolCallsJson = toolCallsJson; }
    public Integer getTokenUsage() { return tokenUsage; }
    public void setTokenUsage(Integer tokenUsage) { this.tokenUsage = tokenUsage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 5: 写两个 Repository**

`ConversationRepository.java`:
```java
package com.agentplatform.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<ConversationEntity, Long> {
    List<ConversationEntity> findByUserIdOrderByUpdatedAtDesc(String userId);
}
```

`MessageRepository.java`:
```java
package com.agentplatform.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<MessageEntity, Long> {
    List<MessageEntity> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
}
```

- [ ] **Step 6: 运行测试确认通过**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=ChatPersistenceTest test`
Expected: PASS。

- [ ] **Step 7: 提交**

```bash
cd /Users/liurongjie/my-brain-extension
git add agent-platform/backend/src/main/java/com/agentplatform/chat agent-platform/backend/src/test/java/com/agentplatform/chat
git commit -m "添加会话与消息实体及仓库"
```

---

### Task 5: 纯对话编排（ChatOrchestrator，流式 + 落库）

**Files:**
- Create: `backend/src/main/java/com/agentplatform/orchestrator/ChatOrchestrator.java`
- Test: `backend/src/test/java/com/agentplatform/orchestrator/ChatOrchestratorTest.java`

**Interfaces:**
- Consumes: `ChatModel`（Spring AI，`@MockBean` 于测试）、`AgentRepository`、`ConversationRepository`、`MessageRepository`、`BusinessException`。
- Produces:
  - `record ChatChunk(Long conversationId, String type, String content)`（`type` ∈ `meta`/`token`/`done`）。
  - `ChatOrchestrator.chat(Long agentId, Long conversationId, String userMessage, String userId)` → `Flux<ChatChunk>`。行为：校验 Agent 存在且 `enabled`（否则 `BusinessException`）；conversationId 为 null 时新建会话（title 取 userMessage 前 20 字）；先落库 user 消息；按历史(系统提示 + 全部历史消息)组装 Prompt 调 `chatModel.stream`；首个 chunk 发 `meta`(带 conversationId)，随后逐段发 `token`，结束发 `done` 并落库 assistant 消息（content 为累计全文）。

- [ ] **Step 1: 写失败测试 ChatOrchestratorTest.java**

```java
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
import reactor.test.StepVerifier;

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

        // 第一个是 meta(带 conversationId)，随后 token，最后 done
        ChatChunk first = flux.blockFirst();
        assertThat(first.type()).isEqualTo("meta");
        assertThat(first.conversationId()).isNotNull();

        // 重新订阅做完整断言(冷流: mock 每次返回新 Flux)
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
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=ChatOrchestratorTest test`
Expected: 编译失败（`ChatOrchestrator`/`ChatChunk` 不存在）。

- [ ] **Step 3: 写 ChatChunk.java 与 ChatOrchestrator.java（两个独立文件）**

`backend/src/main/java/com/agentplatform/orchestrator/ChatChunk.java`:
```java
package com.agentplatform.orchestrator;

public record ChatChunk(Long conversationId, String type, String content) {}
```

`backend/src/main/java/com/agentplatform/orchestrator/ChatOrchestrator.java`:
```java
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

        // 落库用户消息
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
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=ChatOrchestratorTest test`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
cd /Users/liurongjie/my-brain-extension
git add agent-platform/backend/src/main/java/com/agentplatform/orchestrator agent-platform/backend/src/test/java/com/agentplatform/orchestrator
git commit -m "添加纯对话编排: 流式输出与消息落库"
```

---

### Task 6: 对话 SSE 接口 + 会话查询接口

**Files:**
- Create: `backend/src/main/java/com/agentplatform/chat/dto/ChatRequest.java`
- Create: `backend/src/main/java/com/agentplatform/chat/dto/ConversationResponse.java`
- Create: `backend/src/main/java/com/agentplatform/chat/dto/MessageResponse.java`
- Create: `backend/src/main/java/com/agentplatform/chat/ChatController.java`
- Test: `backend/src/test/java/com/agentplatform/chat/ChatControllerTest.java`

**Interfaces:**
- Consumes: `ChatOrchestrator`、`ChatChunk`、`ConversationRepository`、`MessageRepository`、`ApiResponse`、`@MockitoBean ChatModel`（测试）。
- Produces:
  - `ChatRequest`：`Long agentId, Long conversationId, String message, String userId`（record）。
  - `ConversationResponse`：`Long id, Long agentId, String title, LocalDateTime updatedAt`，静态 `from`。
  - `MessageResponse`：`Long id, String role, String content, LocalDateTime createdAt`，静态 `from`。
  - 接口：
    - `POST /api/chat`（produces `text/event-stream`）body=ChatRequest → `Flux<ServerSentEvent<ChatChunk>>`（event name 用 chunk.type）。
    - `GET /api/conversations?userId=` → `ApiResponse<List<ConversationResponse>>`。
    - `GET /api/conversations/{id}/messages` → `ApiResponse<List<MessageResponse>>`。

- [ ] **Step 1: 写失败测试 ChatControllerTest.java**

```java
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

        // MockMvc 异步: 先发起拿到 conversationId 需要从历史接口验证, 这里断言流式 200 且含 token 数据
        var mvcResult = mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        String sse = mvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(sse).contains("Hi").contains("!");

        // 历史: 找到该用户的会话, 再查消息
        String convList = mvc.perform(get("/api/conversations").param("userId", "u-1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(convList).contains("\"code\":0");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=ChatControllerTest test`
Expected: FAIL（无 `/api/chat` 路由）。

- [ ] **Step 3: 写三个 DTO**

`ChatRequest.java`:
```java
package com.agentplatform.chat.dto;

public record ChatRequest(Long agentId, Long conversationId, String message, String userId) {}
```

`ConversationResponse.java`:
```java
package com.agentplatform.chat.dto;

import com.agentplatform.chat.ConversationEntity;

import java.time.LocalDateTime;

public record ConversationResponse(Long id, Long agentId, String title, LocalDateTime updatedAt) {
    public static ConversationResponse from(ConversationEntity c) {
        return new ConversationResponse(c.getId(), c.getAgentId(), c.getTitle(), c.getUpdatedAt());
    }
}
```

`MessageResponse.java`:
```java
package com.agentplatform.chat.dto;

import com.agentplatform.chat.MessageEntity;

import java.time.LocalDateTime;

public record MessageResponse(Long id, String role, String content, LocalDateTime createdAt) {
    public static MessageResponse from(MessageEntity m) {
        return new MessageResponse(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt());
    }
}
```

- [ ] **Step 4: 写 ChatController.java**

```java
package com.agentplatform.chat;

import com.agentplatform.chat.dto.ChatRequest;
import com.agentplatform.chat.dto.ConversationResponse;
import com.agentplatform.chat.dto.MessageResponse;
import com.agentplatform.common.ApiResponse;
import com.agentplatform.orchestrator.ChatChunk;
import com.agentplatform.orchestrator.ChatOrchestrator;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
public class ChatController {

    private final ChatOrchestrator orchestrator;
    private final ConversationRepository conversations;
    private final MessageRepository messages;

    public ChatController(ChatOrchestrator orchestrator,
                          ConversationRepository conversations,
                          MessageRepository messages) {
        this.orchestrator = orchestrator;
        this.conversations = conversations;
        this.messages = messages;
    }

    @PostMapping(value = "/api/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatChunk>> chat(@RequestBody ChatRequest req) {
        return orchestrator.chat(req.agentId(), req.conversationId(), req.message(), req.userId())
                .map(chunk -> ServerSentEvent.<ChatChunk>builder()
                        .event(chunk.type())
                        .data(chunk)
                        .build());
    }

    @GetMapping("/api/conversations")
    public ApiResponse<List<ConversationResponse>> myConversations(@RequestParam String userId) {
        List<ConversationResponse> list = conversations.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream().map(ConversationResponse::from).toList();
        return ApiResponse.ok(list);
    }

    @GetMapping("/api/conversations/{id}/messages")
    public ApiResponse<List<MessageResponse>> history(@PathVariable Long id) {
        List<MessageResponse> list = messages.findByConversationIdOrderByCreatedAtAsc(id)
                .stream().map(MessageResponse::from).toList();
        return ApiResponse.ok(list);
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=ChatControllerTest test`
Expected: PASS。

- [ ] **Step 6: 跑全部测试确认整体绿**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw test`
Expected: 所有测试通过。

- [ ] **Step 7: 提交**

```bash
cd /Users/liurongjie/my-brain-extension
git add agent-platform/backend/src/main/java/com/agentplatform/chat agent-platform/backend/src/test/java/com/agentplatform/chat/ChatControllerTest.java
git commit -m "添加对话 SSE 接口与会话查询接口"
```

---

## Self-Review（对照设计文档与路线图）

- **Agent 配置 CRUD**（设计第五节 admin agents）：Task 1–3 ✅，含用户端 `/api/agents` 仅启用过滤。
- **会话/消息持久化**（设计第三节）：Task 4 ✅，实体映射靠存取往返测试保证（ddl-auto=none）。
- **纯对话 + 流式**（设计第四节 chat 分支、第六节 SSE）：Task 5–6 ✅，`meta/token/done` 三类事件对应设计的 SSE 事件类型。
- **编排骨架**：`ChatOrchestrator` 已建 chat 分支；RAG/工具/ReAct 分支留待计划 03–05，符合路线图边界。
- **模型调用不打真实网络**：所有相关测试用 `@MockitoBean ChatModel` ✅。
- **占位符扫描**：无 TBD/TODO；Task 5 Step 3 明确指出 `ChatChunk` 独立成文件并给出最终两文件内容 ✅。
- **类型一致性**：`ChatChunk(conversationId,type,content)`、`ChatOrchestrator.chat(...)→Flux<ChatChunk>`、Controller 映射 `ServerSentEvent<ChatChunk>` 在各处一致 ✅；Spring AI 1.0 输出文本用 `getOutput().getText()` ✅；`@MockitoBean`（Spring Boot 3.4 起替代 `@MockBean`）✅。
- **风险备注**：`OpenAiChatOptions.builder()` 的 `temperature/topP(Double)`、`maxTokens(Integer)` 签名若在 1.0.0 略有差异，执行时以编译为准微调；Spring MVC 返回 `Flux<ServerSentEvent>` 需 reactor 在类路径（spring-ai 已传递引入）。
