# Agent 平台 · 计划 03：RAG（知识库 + 检索增强）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让管理员能建知识库、上传文本文档（自动切分+向量化存入 Redis），把 Agent 绑定到知识库；对话时按知识库检索增强（带引用来源）。

**Architecture:** 新增 `rag` 包（知识库/文档/处理/检索）。向量存储用自定义 `RedisVectorStore` bean（声明 `kb_id`/`doc_id` 为 tag 元数据字段，实现按 KB 过滤）。文档处理：读文本 → `TokenTextSplitter` 切分 → `EmbeddingModel` 向量化 → 存 Redis，状态机 pending/processing/done/failed。`ChatOrchestrator` 增加 RAG 增强分支。

**Tech Stack:** 沿用前序计划；新增 `spring-ai-starter-vector-store-redis`（底层 RediSearch + Jedis）。

## Global Constraints

- JDK 17；`./mvnw` 前置 `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
- 包根 `com.agentplatform`；注释/命名英文，提交中文
- 集成测试继承 `IntegrationTestBase`（本计划为其新增 **singleton redis-stack 容器** 与 **FakeEmbeddingModel 全局注入**）
- 第一版文档仅支持**纯文本/Markdown**（UTF-8 读取）；PDF/docx 留待后续
- 模型/embedding 调用测试中不打真实网络
- Spring AI 1.0 Redis 向量库 API 若与本计划签名略有出入，以编译为准微调（执行时调整）

## 文件结构（本计划新增/修改）

```
backend/pom.xml                                  # +spring-ai-starter-vector-store-redis
backend/src/main/resources/application.yml       # +spring.data.redis.* 配置
backend/src/main/java/com/agentplatform/
├── infra/VectorStoreConfig.java                 # JedisPooled + 自定义 RedisVectorStore bean
├── rag/
│   ├── KnowledgeBaseEntity.java / KnowledgeBaseRepository.java
│   ├── DocumentEntity.java / DocumentRepository.java
│   ├── KnowledgeBaseService.java                # 知识库 CRUD
│   ├── DocumentService.java                     # 上传(存元数据)+触发处理
│   ├── DocumentProcessingService.java           # 同步 process(docId): 切分+向量化+入库+状态机
│   ├── RagRetriever.java                        # 按 kbIds 检索 topK
│   ├── KnowledgeBaseController.java             # /api/admin/knowledge-bases ...
│   └── dto/ (KnowledgeBaseRequest/Response, DocumentResponse, RetrieveResult)
├── agent/
│   ├── AgentKnowledgeBaseEntity.java / AgentKbId.java / AgentKnowledgeBaseRepository.java
│   └── AgentController.java                      # +PUT /{id}/bindings (本计划只绑 KB)
└── orchestrator/ChatOrchestrator.java           # +RAG 增强分支
backend/src/test/java/com/agentplatform/
├── IntegrationTestBase.java                      # +redis 容器 +@Import(TestEmbeddingConfig)
├── support/TestEmbeddingConfig.java + FakeEmbeddingModel.java
└── rag/*Test.java
```

---

### Task 1: Redis 向量库依赖 + 自定义 bean + 测试基座（redis 容器 + 假 embedding）

**Files:**
- Modify: `backend/pom.xml`（加依赖）
- Modify: `backend/src/main/resources/application.yml`（加 redis 配置）
- Create: `backend/src/main/java/com/agentplatform/infra/VectorStoreConfig.java`
- Create: `backend/src/test/java/com/agentplatform/support/FakeEmbeddingModel.java`
- Create: `backend/src/test/java/com/agentplatform/support/TestEmbeddingConfig.java`
- Modify: `backend/src/test/java/com/agentplatform/IntegrationTestBase.java`
- Test: `backend/src/test/java/com/agentplatform/rag/VectorStoreSmokeTest.java`

**Interfaces:**
- Produces:
  - Spring `VectorStore` bean（Redis 实现，索引 `agent-kb-index`，前缀 `kb:`，元数据 tag 字段 `kb_id`、`doc_id`，`initializeSchema=true`）。
  - 测试基座：`IntegrationTestBase` 额外启动 singleton `redis/redis-stack`，注入 `spring.data.redis.host/port`，并 `@Import(TestEmbeddingConfig.class)` 用 `FakeEmbeddingModel`（8 维、按文本确定性向量，`dimensions()=8`，无网络）。

- [ ] **Step 1: pom.xml 加依赖**

在 `<dependencies>` 中（OpenAI starter 之后）加入：
```xml
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-starter-vector-store-redis</artifactId>
    </dependency>
```

- [ ] **Step 2: application.yml 加 redis 配置**

在 `spring:` 下增加（与现有 `datasource`/`jpa`/`ai` 同级）：
```yaml
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

- [ ] **Step 3: 写 VectorStoreConfig.java**

```java
package com.agentplatform.infra;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

import static org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;

@Configuration
public class VectorStoreConfig {

    @Bean
    public JedisPooled jedisPooled(@Value("${spring.data.redis.host:localhost}") String host,
                                   @Value("${spring.data.redis.port:6379}") int port) {
        return new JedisPooled(host, port);
    }

    @Bean
    public VectorStore vectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName("agent-kb-index")
                .prefix("kb:")
                .metadataFields(MetadataField.tag("kb_id"), MetadataField.tag("doc_id"))
                .initializeSchema(true)
                .build();
    }
}
```

- [ ] **Step 4: 写 FakeEmbeddingModel.java（测试用）**

```java
package com.agentplatform.support;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, network-free embedding for tests: 8-dim vector derived from the
 * text's characters. Equal text -> equal vector (so exact-match queries rank first).
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    private static final int DIM = 8;

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        float[] v = new float[DIM];
        if (text != null) {
            for (int i = 0; i < text.length(); i++) {
                v[i % DIM] += (text.charAt(i) % 32) + 1;
            }
        }
        // normalize to avoid zero vector
        double norm = 0;
        for (float f : v) norm += f * f;
        norm = Math.sqrt(norm) + 1e-6;
        for (int i = 0; i < DIM; i++) v[i] = (float) (v[i] / norm);
        return v;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> instructions = request.getInstructions();
        for (int i = 0; i < instructions.size(); i++) {
            embeddings.add(new Embedding(embed(instructions.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return DIM;
    }
}
```

- [ ] **Step 5: 写 TestEmbeddingConfig.java（测试用，@Primary 覆盖真实 embedding）**

```java
package com.agentplatform.support;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestEmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingModel fakeEmbeddingModel() {
        return new FakeEmbeddingModel();
    }
}
```

- [ ] **Step 6: 修改 IntegrationTestBase.java（加 redis 容器 + 引入假 embedding）**

最终内容：
```java
package com.agentplatform;

import com.agentplatform.support.TestEmbeddingConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest
@Import(TestEmbeddingConfig.class)
public abstract class IntegrationTestBase {

    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0").withDatabaseName("agent_platform");

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis/redis-stack:latest").withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.ai.openai.api-key", () -> "test-key");
    }
}
```

- [ ] **Step 7: 写 VectorStoreSmokeTest.java**

```java
package com.agentplatform.rag;

import com.agentplatform.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VectorStoreSmokeTest extends IntegrationTestBase {

    @Autowired VectorStore vectorStore;

    @Test
    void stores_and_filters_by_kb_id() {
        vectorStore.add(List.of(
                new Document("apple apple apple", Map.of("kb_id", "1001", "doc_id", "d1")),
                new Document("banana banana banana", Map.of("kb_id", "2002", "doc_id", "d2"))
        ));

        List<Document> kb1 = vectorStore.similaritySearch(SearchRequest.builder()
                .query("apple apple apple").topK(5)
                .filterExpression("kb_id == '1001'").build());

        assertThat(kb1).isNotEmpty();
        assertThat(kb1).allMatch(d -> "1001".equals(d.getMetadata().get("kb_id")));
    }
}
```

- [ ] **Step 8: 运行测试确认通过**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=VectorStoreSmokeTest test`
Expected: PASS（先确认 redis-stack 容器与向量库联通、kb_id 过滤生效）。

- [ ] **Step 9: 跑既有全量测试确认未回归**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw test`
Expected: 全绿（新基座对旧测试无破坏；旧测试现在也会起 redis 容器并用假 embedding）。

- [ ] **Step 10: 提交**

```bash
cd /Users/liurongjie/my-brain-extension
git add agent-platform/backend/pom.xml agent-platform/backend/src/main/resources/application.yml agent-platform/backend/src/main/java/com/agentplatform/infra/VectorStoreConfig.java agent-platform/backend/src/test/java/com/agentplatform/support agent-platform/backend/src/test/java/com/agentplatform/IntegrationTestBase.java agent-platform/backend/src/test/java/com/agentplatform/rag/VectorStoreSmokeTest.java
git commit -m "接入 Redis 向量库: 自定义 RedisVectorStore(kb_id 过滤) 与测试基座"
```

---

### Task 2: 知识库 CRUD（实体/仓库/服务/接口）

**Files:**
- Create: `backend/src/main/java/com/agentplatform/rag/KnowledgeBaseEntity.java`
- Create: `backend/src/main/java/com/agentplatform/rag/KnowledgeBaseRepository.java`
- Create: `backend/src/main/java/com/agentplatform/rag/dto/KnowledgeBaseRequest.java`
- Create: `backend/src/main/java/com/agentplatform/rag/dto/KnowledgeBaseResponse.java`
- Create: `backend/src/main/java/com/agentplatform/rag/KnowledgeBaseService.java`
- Create: `backend/src/main/java/com/agentplatform/rag/KnowledgeBaseController.java`
- Test: `backend/src/test/java/com/agentplatform/rag/KnowledgeBaseServiceTest.java`

**Interfaces:**
- Produces:
  - `KnowledgeBaseEntity`：`Long id, String name, String description, String embeddingModel, Integer chunkSize, Integer chunkOverlap, LocalDateTime createdAt`。
  - `KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, Long>`。
  - `KnowledgeBaseRequest(String name, String description, String embeddingModel, Integer chunkSize, Integer chunkOverlap)`。
  - `KnowledgeBaseResponse`（全字段 + id + createdAt，静态 `from`）。
  - `KnowledgeBaseService`：`create/listAll/get/delete`；create 默认值：`embeddingModel="text-embedding-3-small"`, `chunkSize=800`, `chunkOverlap=100`；`get` 缺失抛 `BusinessException(40403,"knowledge base not found")`。
  - 接口前缀 `/api/admin/knowledge-bases`：`POST`、`GET`、`GET /{id}`、`DELETE /{id}`，均包 `ApiResponse`。

- [ ] **Step 1: 写失败测试 KnowledgeBaseServiceTest.java**

```java
package com.agentplatform.rag;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.rag.dto.KnowledgeBaseRequest;
import com.agentplatform.rag.dto.KnowledgeBaseResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseServiceTest extends IntegrationTestBase {

    @Autowired KnowledgeBaseService service;

    @Test
    void create_applies_defaults() {
        KnowledgeBaseResponse r = service.create(
                new KnowledgeBaseRequest("产品手册", null, null, null, null));
        assertThat(r.id()).isNotNull();
        assertThat(r.embeddingModel()).isEqualTo("text-embedding-3-small");
        assertThat(r.chunkSize()).isEqualTo(800);
        assertThat(r.chunkOverlap()).isEqualTo(100);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=KnowledgeBaseServiceTest test`
Expected: 编译失败。

- [ ] **Step 3: 写 KnowledgeBaseEntity.java**

```java
package com.agentplatform.rag;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_base")
public class KnowledgeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;

    @Column(name = "embedding_model")
    private String embeddingModel;

    @Column(name = "chunk_size")
    private Integer chunkSize;

    @Column(name = "chunk_overlap")
    private Integer chunkOverlap;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public Integer getChunkSize() { return chunkSize; }
    public void setChunkSize(Integer chunkSize) { this.chunkSize = chunkSize; }
    public Integer getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(Integer chunkOverlap) { this.chunkOverlap = chunkOverlap; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 4: 写 KnowledgeBaseRepository.java**

```java
package com.agentplatform.rag;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, Long> {
}
```

- [ ] **Step 5: 写两个 DTO**

`dto/KnowledgeBaseRequest.java`:
```java
package com.agentplatform.rag.dto;

public record KnowledgeBaseRequest(
        String name,
        String description,
        String embeddingModel,
        Integer chunkSize,
        Integer chunkOverlap
) {}
```

`dto/KnowledgeBaseResponse.java`:
```java
package com.agentplatform.rag.dto;

import com.agentplatform.rag.KnowledgeBaseEntity;

import java.time.LocalDateTime;

public record KnowledgeBaseResponse(
        Long id,
        String name,
        String description,
        String embeddingModel,
        Integer chunkSize,
        Integer chunkOverlap,
        LocalDateTime createdAt
) {
    public static KnowledgeBaseResponse from(KnowledgeBaseEntity e) {
        return new KnowledgeBaseResponse(e.getId(), e.getName(), e.getDescription(),
                e.getEmbeddingModel(), e.getChunkSize(), e.getChunkOverlap(), e.getCreatedAt());
    }
}
```

- [ ] **Step 6: 写 KnowledgeBaseService.java**

```java
package com.agentplatform.rag;

import com.agentplatform.common.BusinessException;
import com.agentplatform.rag.dto.KnowledgeBaseRequest;
import com.agentplatform.rag.dto.KnowledgeBaseResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository repository;

    public KnowledgeBaseService(KnowledgeBaseRepository repository) {
        this.repository = repository;
    }

    public KnowledgeBaseResponse create(KnowledgeBaseRequest req) {
        KnowledgeBaseEntity e = new KnowledgeBaseEntity();
        e.setName(req.name());
        e.setDescription(req.description());
        e.setEmbeddingModel(req.embeddingModel() != null ? req.embeddingModel() : "text-embedding-3-small");
        e.setChunkSize(req.chunkSize() != null ? req.chunkSize() : 800);
        e.setChunkOverlap(req.chunkOverlap() != null ? req.chunkOverlap() : 100);
        return KnowledgeBaseResponse.from(repository.save(e));
    }

    public List<KnowledgeBaseResponse> listAll() {
        return repository.findAll().stream().map(KnowledgeBaseResponse::from).toList();
    }

    public KnowledgeBaseEntity getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(40403, "knowledge base not found"));
    }

    public KnowledgeBaseResponse get(Long id) {
        return KnowledgeBaseResponse.from(getEntity(id));
    }

    public void delete(Long id) {
        repository.delete(getEntity(id));
    }
}
```

- [ ] **Step 7: 写 KnowledgeBaseController.java**

```java
package com.agentplatform.rag;

import com.agentplatform.common.ApiResponse;
import com.agentplatform.rag.dto.KnowledgeBaseRequest;
import com.agentplatform.rag.dto.KnowledgeBaseResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService service;

    public KnowledgeBaseController(KnowledgeBaseService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseResponse> create(@RequestBody KnowledgeBaseRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseResponse>> listAll() {
        return ApiResponse.ok(service.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBaseResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
```

- [ ] **Step 8: 运行测试确认通过**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=KnowledgeBaseServiceTest test`
Expected: PASS。

- [ ] **Step 9: 提交**

```bash
cd /Users/liurongjie/my-brain-extension
git add agent-platform/backend/src/main/java/com/agentplatform/rag agent-platform/backend/src/test/java/com/agentplatform/rag/KnowledgeBaseServiceTest.java
git commit -m "添加知识库 CRUD"
```

---

### Task 3: 文档实体 + 上传接口（存元数据 + 读取文本）

**Files:**
- Create: `backend/src/main/java/com/agentplatform/rag/DocumentEntity.java`
- Create: `backend/src/main/java/com/agentplatform/rag/DocumentRepository.java`
- Create: `backend/src/main/java/com/agentplatform/rag/dto/DocumentResponse.java`
- Create: `backend/src/main/java/com/agentplatform/rag/DocumentService.java`
- Modify: `backend/src/main/java/com/agentplatform/rag/KnowledgeBaseController.java`（加上传/列表/删除文档接口）
- Test: `backend/src/test/java/com/agentplatform/rag/DocumentServiceTest.java`

**Interfaces:**
- Consumes: `KnowledgeBaseService.getEntity`、`DocumentProcessingService`（Task 4，本任务先以接口占位：用一个 `process(Long)` 空实现的临时桩？——不需要：本任务 `DocumentService.upload` 只存元数据并返回，处理在 Task 4 接入。为避免前后依赖，本任务 `upload` 暂不触发处理）。
- Produces:
  - `DocumentEntity`：`Long id, Long kbId, String filename, String fileType, String status, Integer chunkCount, LocalDateTime createdAt`。
  - `DocumentRepository`：`findByKbIdOrderByCreatedAtDesc(Long kbId)`。
  - `DocumentResponse`（全字段，静态 `from`）。
  - `DocumentService.upload(Long kbId, String filename, String fileType, String content)`：校验 KB 存在；存 `DocumentEntity`（status=`pending`），返回 `DocumentResponse`；并把原始文本暂存到内存 map 供 Task 4 处理（key=docId）。`list(Long kbId)`、`delete(Long docId)`。
  - 接口：`POST /api/admin/knowledge-bases/{id}/documents`（`multipart/form-data`，字段 `file`）、`GET /{id}/documents`、`DELETE /{id}/documents/{docId}`。

> 说明：原始文本暂存用 `DocumentService` 内的 `ConcurrentHashMap<Long,String> rawTextById`。这是第一版的简化（不落地原文到磁盘/库）。Task 4 的处理从该 map 取文本；处理完清除。生产可改为存对象存储，留待后续。

- [ ] **Step 1: 写失败测试 DocumentServiceTest.java**

```java
package com.agentplatform.rag;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.rag.dto.DocumentResponse;
import com.agentplatform.rag.dto.KnowledgeBaseRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentServiceTest extends IntegrationTestBase {

    @Autowired DocumentService documentService;
    @Autowired KnowledgeBaseService kbService;

    @Test
    void upload_persists_pending_document_and_keeps_text() {
        Long kbId = kbService.create(new KnowledgeBaseRequest("kb", null, null, null, null)).id();
        DocumentResponse doc = documentService.upload(kbId, "a.txt", "txt", "hello world content");
        assertThat(doc.id()).isNotNull();
        assertThat(doc.status()).isEqualTo("pending");
        assertThat(documentService.rawText(doc.id())).isEqualTo("hello world content");
        assertThat(documentService.list(kbId)).extracting(DocumentResponse::id).contains(doc.id());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=DocumentServiceTest test`
Expected: 编译失败。

- [ ] **Step 3: 写 DocumentEntity.java**

```java
package com.agentplatform.rag;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "document")
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kb_id")
    private Long kbId;

    private String filename;

    @Column(name = "file_type")
    private String fileType;

    private String status;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 4: 写 DocumentRepository.java**

```java
package com.agentplatform.rag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
    List<DocumentEntity> findByKbIdOrderByCreatedAtDesc(Long kbId);
}
```

- [ ] **Step 5: 写 DocumentResponse.java**

```java
package com.agentplatform.rag.dto;

import com.agentplatform.rag.DocumentEntity;

import java.time.LocalDateTime;

public record DocumentResponse(
        Long id, Long kbId, String filename, String fileType,
        String status, Integer chunkCount, LocalDateTime createdAt
) {
    public static DocumentResponse from(DocumentEntity e) {
        return new DocumentResponse(e.getId(), e.getKbId(), e.getFilename(), e.getFileType(),
                e.getStatus(), e.getChunkCount(), e.getCreatedAt());
    }
}
```

- [ ] **Step 6: 写 DocumentService.java**

```java
package com.agentplatform.rag;

import com.agentplatform.common.BusinessException;
import com.agentplatform.rag.dto.DocumentResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DocumentService {

    private final DocumentRepository repository;
    private final KnowledgeBaseService kbService;
    private final ConcurrentHashMap<Long, String> rawTextById = new ConcurrentHashMap<>();

    public DocumentService(DocumentRepository repository, KnowledgeBaseService kbService) {
        this.repository = repository;
        this.kbService = kbService;
    }

    public DocumentResponse upload(Long kbId, String filename, String fileType, String content) {
        kbService.getEntity(kbId); // validate exists
        DocumentEntity d = new DocumentEntity();
        d.setKbId(kbId);
        d.setFilename(filename);
        d.setFileType(fileType);
        d.setStatus("pending");
        d.setChunkCount(0);
        DocumentEntity saved = repository.save(d);
        rawTextById.put(saved.getId(), content != null ? content : "");
        return DocumentResponse.from(saved);
    }

    public String rawText(Long docId) {
        return rawTextById.get(docId);
    }

    public void clearRawText(Long docId) {
        rawTextById.remove(docId);
    }

    public List<DocumentResponse> list(Long kbId) {
        return repository.findByKbIdOrderByCreatedAtDesc(kbId).stream()
                .map(DocumentResponse::from).toList();
    }

    public DocumentEntity getEntity(Long docId) {
        return repository.findById(docId)
                .orElseThrow(() -> new BusinessException(40404, "document not found"));
    }

    public void delete(Long docId) {
        repository.delete(getEntity(docId));
        rawTextById.remove(docId);
    }
}
```

- [ ] **Step 7: 给 KnowledgeBaseController 加文档接口**

在 `KnowledgeBaseController` 中追加注入 `DocumentService` 并加方法（构造器改为同时注入两个 service）：
```java
    // 构造器改为:
    // public KnowledgeBaseController(KnowledgeBaseService service, DocumentService documentService) {...}

    @org.springframework.web.bind.annotation.PostMapping(value = "/{id}/documents",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public com.agentplatform.common.ApiResponse<com.agentplatform.rag.dto.DocumentResponse> upload(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam("file")
            org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        String filename = file.getOriginalFilename();
        String type = filename != null && filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1) : "txt";
        String content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        return com.agentplatform.common.ApiResponse.ok(documentService.upload(id, filename, type, content));
    }

    @org.springframework.web.bind.annotation.GetMapping("/{id}/documents")
    public com.agentplatform.common.ApiResponse<java.util.List<com.agentplatform.rag.dto.DocumentResponse>> listDocs(
            @PathVariable Long id) {
        return com.agentplatform.common.ApiResponse.ok(documentService.list(id));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}/documents/{docId}")
    public com.agentplatform.common.ApiResponse<Void> deleteDoc(
            @PathVariable Long id, @PathVariable Long docId) {
        documentService.delete(docId);
        return com.agentplatform.common.ApiResponse.ok(null);
    }
```
> 落地时把 `KnowledgeBaseController` 的字段与构造器改为同时持有 `KnowledgeBaseService service` 与 `DocumentService documentService`，并补 `import` 使代码整洁（上面用全限定名是为减少改动歧义，落地可改成顶部 import）。

- [ ] **Step 8: 运行测试确认通过**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=DocumentServiceTest test`
Expected: PASS。

- [ ] **Step 9: 提交**

```bash
cd /Users/liurongjie/my-brain-extension
git add agent-platform/backend/src/main/java/com/agentplatform/rag agent-platform/backend/src/test/java/com/agentplatform/rag/DocumentServiceTest.java
git commit -m "添加文档实体与上传接口(暂存原文)"
```

---

### Task 4: 文档处理（切分 + 向量化 + 入库 + 状态机）

**Files:**
- Create: `backend/src/main/java/com/agentplatform/rag/DocumentProcessingService.java`
- Modify: `backend/src/main/java/com/agentplatform/rag/DocumentService.java`（upload 后异步触发处理）
- Test: `backend/src/test/java/com/agentplatform/rag/DocumentProcessingServiceTest.java`

**Interfaces:**
- Consumes: `DocumentService`（取原文/状态）、`KnowledgeBaseService`（切分参数）、`VectorStore`、`DocumentRepository`。
- Produces:
  - `DocumentProcessingService.process(Long docId)`（同步）：置 `processing` → 用 `TokenTextSplitter` 切分原文 → 每个切片包成 `Document`（metadata `kb_id`、`doc_id`）→ `vectorStore.add` → 写回 `chunkCount` 与 `status=done`；异常置 `failed`。处理后 `documentService.clearRawText(docId)`。
  - `DocumentService.upload` 末尾通过注入的 `DocumentProcessingService` 异步触发 `process`（用 `@Async` 或 `TaskExecutor`）。为测试可控，处理逻辑全在同步 `process` 中，测试直接调用 `process`。

- [ ] **Step 1: 写失败测试 DocumentProcessingServiceTest.java**

```java
package com.agentplatform.rag;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.rag.dto.DocumentResponse;
import com.agentplatform.rag.dto.KnowledgeBaseRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentProcessingServiceTest extends IntegrationTestBase {

    @Autowired DocumentProcessingService processing;
    @Autowired DocumentService documentService;
    @Autowired KnowledgeBaseService kbService;
    @Autowired VectorStore vectorStore;

    @Test
    void process_chunks_embeds_and_marks_done() {
        Long kbId = kbService.create(new KnowledgeBaseRequest("kb", null, null, null, null)).id();
        String text = "Spring AI 是一个用于构建 AI 应用的框架。".repeat(50);
        DocumentResponse doc = documentService.upload(kbId, "g.txt", "txt", text);

        processing.process(doc.id());

        DocumentEntity after = documentService.getEntity(doc.id());
        assertThat(after.getStatus()).isEqualTo("done");
        assertThat(after.getChunkCount()).isGreaterThan(0);

        // 该 KB 下可检索到内容
        var hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query("Spring AI 框架").topK(3)
                .filterExpression("kb_id == '" + kbId + "'").build());
        assertThat(hits).isNotEmpty();
        assertThat(hits).allMatch(d -> String.valueOf(kbId).equals(d.getMetadata().get("kb_id")));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=DocumentProcessingServiceTest test`
Expected: 编译失败。

- [ ] **Step 3: 写 DocumentProcessingService.java**

```java
package com.agentplatform.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DocumentProcessingService {

    private final DocumentService documentService;
    private final KnowledgeBaseService kbService;
    private final DocumentRepository documentRepository;
    private final VectorStore vectorStore;

    public DocumentProcessingService(DocumentService documentService,
                                     KnowledgeBaseService kbService,
                                     DocumentRepository documentRepository,
                                     VectorStore vectorStore) {
        this.documentService = documentService;
        this.kbService = kbService;
        this.documentRepository = documentRepository;
        this.vectorStore = vectorStore;
    }

    public void process(Long docId) {
        DocumentEntity doc = documentService.getEntity(docId);
        doc.setStatus("processing");
        documentRepository.save(doc);
        try {
            KnowledgeBaseEntity kb = kbService.getEntity(doc.getKbId());
            String text = documentService.rawText(docId);
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("empty document text");
            }

            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(kb.getChunkSize())
                    .build();
            Document raw = new Document(text);
            List<Document> chunks = splitter.apply(List.of(raw));

            List<Document> toStore = new ArrayList<>();
            for (Document c : chunks) {
                toStore.add(new Document(c.getText(), Map.of(
                        "kb_id", String.valueOf(doc.getKbId()),
                        "doc_id", String.valueOf(docId))));
            }
            vectorStore.add(toStore);

            doc.setChunkCount(toStore.size());
            doc.setStatus("done");
            documentRepository.save(doc);
        } catch (Exception e) {
            doc.setStatus("failed");
            documentRepository.save(doc);
        } finally {
            documentService.clearRawText(docId);
        }
    }
}
```

- [ ] **Step 4: DocumentService.upload 末尾异步触发处理**

给 `DocumentService` 注入 `DocumentProcessingService` 与一个简单线程池，在 `upload` 返回前提交后台处理。改动：
1. 类上方加字段：
```java
    private final java.util.concurrent.ExecutorService executor =
            java.util.concurrent.Executors.newFixedThreadPool(2);
    private DocumentProcessingService processingService; // setter 注入避免循环依赖
```
2. setter（用 `@org.springframework.beans.factory.annotation.Autowired` + `@org.springframework.context.annotation.Lazy`）：
```java
    @org.springframework.beans.factory.annotation.Autowired
    public void setProcessingService(@org.springframework.context.annotation.Lazy DocumentProcessingService p) {
        this.processingService = p;
    }
```
3. `upload` 在 `return` 前：
```java
        Long id = saved.getId();
        executor.submit(() -> processingService.process(id));
```
> `DocumentProcessingService` 依赖 `DocumentService`，`DocumentService` 又触发它，故用 `@Lazy` setter 注入打破循环。测试直接调 `process`，不依赖异步。

- [ ] **Step 5: 运行测试确认通过**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=DocumentProcessingServiceTest test`
Expected: PASS。

- [ ] **Step 6: 提交**

```bash
cd /Users/liurongjie/my-brain-extension
git add agent-platform/backend/src/main/java/com/agentplatform/rag agent-platform/backend/src/test/java/com/agentplatform/rag/DocumentProcessingServiceTest.java
git commit -m "添加文档处理: 切分/向量化/入库与状态机, 上传后异步处理"
```

---

### Task 5: 检索服务 + 检索测试接口

**Files:**
- Create: `backend/src/main/java/com/agentplatform/rag/RagRetriever.java`
- Create: `backend/src/main/java/com/agentplatform/rag/dto/RetrieveResult.java`
- Modify: `backend/src/main/java/com/agentplatform/rag/KnowledgeBaseController.java`（加检索测试接口）
- Test: `backend/src/test/java/com/agentplatform/rag/RagRetrieverTest.java`

**Interfaces:**
- Consumes: `VectorStore`。
- Produces:
  - `record RetrieveResult(String content, Long kbId, Long docId, Double score)`。
  - `RagRetriever.retrieve(List<Long> kbIds, String query, int topK)` → `List<RetrieveResult>`：对每个 kbId 用 `kb_id == 'X'` 过滤检索 topK，合并返回。
  - 接口：`POST /api/admin/knowledge-bases/{id}/retrieve` body=`{"query": "...", "topK": 3}` → `ApiResponse<List<RetrieveResult>>`（调试用）。

- [ ] **Step 1: 写失败测试 RagRetrieverTest.java**

```java
package com.agentplatform.rag;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.rag.dto.KnowledgeBaseRequest;
import com.agentplatform.rag.dto.RetrieveResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagRetrieverTest extends IntegrationTestBase {

    @Autowired RagRetriever retriever;
    @Autowired DocumentProcessingService processing;
    @Autowired DocumentService documentService;
    @Autowired KnowledgeBaseService kbService;

    @Test
    void retrieves_only_from_given_kb() {
        Long kbId = kbService.create(new KnowledgeBaseRequest("kb", null, null, null, null)).id();
        var doc = documentService.upload(kbId, "x.txt", "txt", "KEYWORD_ALPHA 出现在这里。".repeat(20));
        processing.process(doc.id());

        List<RetrieveResult> results = retriever.retrieve(List.of(kbId), "KEYWORD_ALPHA", 3);
        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(r -> kbId.equals(r.kbId()));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=RagRetrieverTest test`
Expected: 编译失败。

- [ ] **Step 3: 写 RetrieveResult.java**

```java
package com.agentplatform.rag.dto;

public record RetrieveResult(String content, Long kbId, Long docId, Double score) {}
```

- [ ] **Step 4: 写 RagRetriever.java**

```java
package com.agentplatform.rag;

import com.agentplatform.rag.dto.RetrieveResult;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RagRetriever {

    private final VectorStore vectorStore;

    public RagRetriever(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<RetrieveResult> retrieve(List<Long> kbIds, String query, int topK) {
        List<RetrieveResult> all = new ArrayList<>();
        for (Long kbId : kbIds) {
            List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .filterExpression("kb_id == '" + kbId + "'")
                    .build());
            for (Document d : hits) {
                Long docId = parseLong(d.getMetadata().get("doc_id"));
                all.add(new RetrieveResult(d.getText(), kbId, docId, d.getScore()));
            }
        }
        return all;
    }

    private Long parseLong(Object v) {
        try {
            return v == null ? null : Long.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

> `Document.getScore()` 在 Spring AI 1.0 返回相似度分值（可能为 `null`）；若该方法名不同，落地以编译为准（备选 `d.getMetadata().get("distance")`）。

- [ ] **Step 5: 给 KnowledgeBaseController 加检索测试接口**

注入 `RagRetriever`，加：
```java
    public record RetrieveRequest(String query, Integer topK) {}

    @org.springframework.web.bind.annotation.PostMapping("/{id}/retrieve")
    public com.agentplatform.common.ApiResponse<java.util.List<com.agentplatform.rag.dto.RetrieveResult>> retrieve(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody RetrieveRequest req) {
        int k = req.topK() != null ? req.topK() : 3;
        return com.agentplatform.common.ApiResponse.ok(
                ragRetriever.retrieve(java.util.List.of(id), req.query(), k));
    }
```
> 把 `ragRetriever` 加入控制器构造器注入。

- [ ] **Step 6: 运行测试确认通过**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=RagRetrieverTest test`
Expected: PASS。

- [ ] **Step 7: 提交**

```bash
cd /Users/liurongjie/my-brain-extension
git add agent-platform/backend/src/main/java/com/agentplatform/rag agent-platform/backend/src/test/java/com/agentplatform/rag/RagRetrieverTest.java
git commit -m "添加 RAG 检索服务与检索测试接口"
```

---

### Task 6: Agent-知识库绑定 + 编排接入 RAG 增强

**Files:**
- Create: `backend/src/main/java/com/agentplatform/agent/AgentKnowledgeBaseEntity.java`
- Create: `backend/src/main/java/com/agentplatform/agent/AgentKbId.java`
- Create: `backend/src/main/java/com/agentplatform/agent/AgentKnowledgeBaseRepository.java`
- Modify: `backend/src/main/java/com/agentplatform/agent/AgentController.java`（加 `PUT /{id}/bindings`）
- Modify: `backend/src/main/java/com/agentplatform/orchestrator/ChatOrchestrator.java`（RAG 增强）
- Test: `backend/src/test/java/com/agentplatform/orchestrator/RagAugmentationTest.java`

**Interfaces:**
- Produces:
  - `AgentKnowledgeBaseEntity`（`@IdClass(AgentKbId.class)`，字段 `Long agentId, Long kbId`，表 `agent_knowledge_base`）。
  - `AgentKnowledgeBaseRepository`：`findByAgentId(Long)`、`deleteByAgentId(Long)`（`@Modifying`）。
  - `AgentController` 加 `PUT /api/admin/agents/{id}/bindings` body=`{"kbIds":[...]}`：重置该 agent 的 KB 绑定。
  - `ChatOrchestrator.chat` 增强：若该 agent 有绑定 KB，则在调用模型前用 `RagRetriever.retrieve(boundKbIds, userMessage, 4)` 取片段，拼成参考资料注入为一条 `SystemMessage`（在历史之后、用户消息之前）。

- [ ] **Step 1: 写失败测试 RagAugmentationTest.java**

```java
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
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=RagAugmentationTest test`
Expected: 编译失败。

- [ ] **Step 3: 写 AgentKbId.java**

```java
package com.agentplatform.agent;

import java.io.Serializable;
import java.util.Objects;

public class AgentKbId implements Serializable {
    private Long agentId;
    private Long kbId;

    public AgentKbId() {}
    public AgentKbId(Long agentId, Long kbId) { this.agentId = agentId; this.kbId = kbId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentKbId that)) return false;
        return Objects.equals(agentId, that.agentId) && Objects.equals(kbId, that.kbId);
    }

    @Override
    public int hashCode() { return Objects.hash(agentId, kbId); }
}
```

- [ ] **Step 4: 写 AgentKnowledgeBaseEntity.java**

```java
package com.agentplatform.agent;

import jakarta.persistence.*;

@Entity
@Table(name = "agent_knowledge_base")
@IdClass(AgentKbId.class)
public class AgentKnowledgeBaseEntity {

    @Id
    @Column(name = "agent_id")
    private Long agentId;

    @Id
    @Column(name = "kb_id")
    private Long kbId;

    public AgentKnowledgeBaseEntity() {}
    public AgentKnowledgeBaseEntity(Long agentId, Long kbId) {
        this.agentId = agentId;
        this.kbId = kbId;
    }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
}
```

- [ ] **Step 5: 写 AgentKnowledgeBaseRepository.java**

```java
package com.agentplatform.agent;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface AgentKnowledgeBaseRepository extends JpaRepository<AgentKnowledgeBaseEntity, AgentKbId> {
    List<AgentKnowledgeBaseEntity> findByAgentId(Long agentId);

    @Modifying
    @Transactional
    void deleteByAgentId(Long agentId);
}
```

- [ ] **Step 6: 给 AgentController 加绑定接口**

注入 `AgentKnowledgeBaseRepository`，加：
```java
    public record BindingsRequest(java.util.List<Long> kbIds) {}

    @PutMapping("/api/admin/agents/{id}/bindings")
    public ApiResponse<Void> bindings(@PathVariable Long id,
                                      @RequestBody BindingsRequest req) {
        service.get(id); // validate agent exists
        agentKnowledgeBaseRepository.deleteByAgentId(id);
        if (req.kbIds() != null) {
            for (Long kbId : req.kbIds()) {
                agentKnowledgeBaseRepository.save(new com.agentplatform.agent.AgentKnowledgeBaseEntity(id, kbId));
            }
        }
        return ApiResponse.ok(null);
    }
```
> 把 `agentKnowledgeBaseRepository` 加入构造器注入。

- [ ] **Step 7: 改 ChatOrchestrator 注入绑定与检索并做 RAG 增强**

1. 构造器追加注入 `AgentKnowledgeBaseRepository bindings` 与 `RagRetriever retriever`（保留原有四个依赖）。
2. 在 `buildPrompt` 里、把历史加入后、生成 options 前，插入 RAG 片段（改 `buildPrompt` 签名为 `buildPrompt(AgentEntity agent, Long convId, String userMessage)`，并在 `chat` 中传入 `userMessage`）：
```java
        // RAG augmentation: inject retrieved context if the agent is bound to KBs
        List<Long> kbIds = bindings.findByAgentId(agent.getId()).stream()
                .map(com.agentplatform.agent.AgentKnowledgeBaseEntity::getKbId).toList();
        if (!kbIds.isEmpty()) {
            var hits = retriever.retrieve(kbIds, userMessage, 4);
            if (!hits.isEmpty()) {
                StringBuilder ctx = new StringBuilder("参考资料(请优先依据以下内容回答):\n");
                for (var h : hits) {
                    ctx.append("- ").append(h.content()).append("\n");
                }
                msgs.add(new SystemMessage(ctx.toString()));
            }
        }
```
> 注意：`buildPrompt` 中插入位置在"系统提示 + 历史消息"之后；历史最后一条即本轮用户消息（chat 中已先落库用户消息）。RAG 上下文作为附加 SystemMessage 追加到末尾即可。需要在 `chat` 调用处把 `buildPrompt(agent, convId)` 改为 `buildPrompt(agent, convId, userMessage)`。

- [ ] **Step 8: 运行测试确认通过**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -q -Dtest=RagAugmentationTest test`
Expected: PASS。

- [ ] **Step 9: 跑全部测试确认整体绿**

Run: `cd agent-platform/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw test`
Expected: 全部通过。

- [ ] **Step 10: 提交**

```bash
cd /Users/liurongjie/my-brain-extension
git add agent-platform/backend/src/main/java/com/agentplatform/agent agent-platform/backend/src/main/java/com/agentplatform/orchestrator agent-platform/backend/src/test/java/com/agentplatform/orchestrator/RagAugmentationTest.java
git commit -m "添加 Agent-知识库绑定与编排 RAG 增强"
```

---

## Self-Review（对照设计与路线图）

- **知识库/文档 CRUD + 上传 + 处理状态**（设计第五节 knowledge-bases）：Task 2–4 ✅。
- **切分/向量化/Redis 存储**（设计第三/四节 RAG）：Task 1（向量库）+ Task 4（处理）✅，`kb_id` 过滤实现隔离。
- **检索 + 检索测试接口**：Task 5 ✅。
- **Agent 绑定知识库 + 检索增强**（设计第四节 RAG 流程、bindings 接口）：Task 6 ✅，引用内容注入 prompt。
- **异步处理 + 状态轮询**（设计第六节）：Task 4 upload 异步触发，document.status 供前端轮询 ✅。
- **不打真实网络**：FakeEmbeddingModel 全局注入 + ChatModel mock ✅。
- **占位符扫描**：无 TBD；Task 3/4 的简化（原文内存暂存）已显式标注为第一版取舍并说明后续方向，非占位 ✅。
- **类型一致性**：`RetrieveResult`、`RagRetriever.retrieve`、`AgentKnowledgeBaseEntity(agentId,kbId)` 在各处一致 ✅。
- **风险点（执行时以编译为准微调）**：Spring AI 1.0 的 `RedisVectorStore.MetadataField`/`builder`、`TokenTextSplitter.builder().withChunkSize`、`Document.getText()/getScore()`、`SearchRequest.builder().filterExpression(...)` 等 API 签名；过滤表达式中 `kb_id` 为 tag,值用单引号字符串。
