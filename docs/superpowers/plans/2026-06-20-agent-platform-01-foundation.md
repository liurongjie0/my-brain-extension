# Agent 平台 · 计划 01：地基 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭起 Agent 平台后端工程地基：本地 Docker 起 MySQL + Redis Stack，Spring Boot 工程可启动，Flyway 建好全部数据表，统一返回/异常处理就位，OpenAI 兼容的 ChatModel/EmbeddingModel 配置可用，health 接口可访问。

**Architecture:** 模块化单体 Spring Boot 工程。本计划只建"地基"层：工程脚手架、基础设施编排、数据库迁移、通用工具（统一返回包装 + 全局异常）、模型配置、健康检查。各业务模块（agent/rag/tool/orchestrator）在后续计划中加入。

**Tech Stack:** JDK 17 · Maven · Spring Boot 3.4.x · Spring AI BOM 1.0.0（`spring-ai-starter-model-openai`）· Spring Data JPA · MySQL 8 · Flyway · Redis Stack（本计划仅起容器，向量库依赖在计划 03 引入）· JUnit5 + Testcontainers

## Global Constraints

- JDK 17，Maven 构建（用 `./mvnw`）
- Spring Boot 3.4.x，Spring AI BOM 严格 `1.0.0`
- 包根：`com.agentplatform`
- 代码注释/命名用英文；提交信息用中文（遵循 CLAUDE.md）
- 敏感配置走环境变量/`.env`，不硬编码密钥
- 模型调用在测试中一律不打真实网络
- 工程目录：仓库下新建 `agent-platform/backend/`

---

## 文件结构

```
agent-platform/
├── docker-compose.yml                         # mysql + redis-stack
├── .env.example                               # 配置模板(标注获取路径)
└── backend/
    ├── mvnw / mvnw.cmd / .mvn/                 # Maven wrapper
    ├── pom.xml                                 # 依赖与 BOM
    └── src/
        ├── main/java/com/agentplatform/
        │   ├── AgentPlatformApplication.java   # 启动类
        │   ├── common/
        │   │   ├── ApiResponse.java            # 统一返回包装
        │   │   ├── BusinessException.java       # 业务异常
        │   │   └── GlobalExceptionHandler.java  # 全局异常处理
        │   ├── infra/
        │   │   └── ModelConfig.java            # ChatModel/EmbeddingModel 说明性配置
        │   └── health/
        │       └── HealthController.java       # GET /api/health
        ├── main/resources/
        │   ├── application.yml                 # 主配置
        │   └── db/migration/
        │       └── V1__init_schema.sql         # 建全部表
        └── test/java/com/agentplatform/
            ├── IntegrationTestBase.java         # Testcontainers MySQL 基类
            ├── common/ApiResponseTest.java
            ├── common/GlobalExceptionHandlerTest.java
            ├── migration/FlywayMigrationTest.java
            ├── infra/ModelConfigTest.java
            └── health/HealthControllerTest.java
```

---

### Task 1: 基础设施编排（docker-compose + .env.example）

**Files:**
- Create: `agent-platform/docker-compose.yml`
- Create: `agent-platform/.env.example`

**Interfaces:**
- Produces: MySQL 监听 `3306`（库 `agent_platform`，用户 `agent`/`agent123`）；Redis Stack 监听 `6379`（RedisInsight `8001`）。后续任务的 `application.yml` 依赖这些端口与凭据。

- [ ] **Step 1: 写 docker-compose.yml**

```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: agent-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: agent_platform
      MYSQL_USER: agent
      MYSQL_PASSWORD: agent123
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-proot123"]
      interval: 5s
      timeout: 3s
      retries: 20

  redis-stack:
    image: redis/redis-stack:latest
    container_name: agent-redis
    restart: unless-stopped
    ports:
      - "6379:6379"
      - "8001:8001"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 20

volumes:
  mysql-data:
  redis-data:
```

- [ ] **Step 2: 写 .env.example**

```bash
# ===== 模型（OpenAI 兼容接口）=====
# OpenAI 兼容网关地址（自建网关填内网地址；官方填 https://api.openai.com）
OPENAI_BASE_URL=https://api.openai.com
# API Key（OpenAI 官方控制台 或 自建网关分发）
OPENAI_API_KEY=sk-xxxx
# 对话模型名（如 gpt-4o-mini / qwen-max，取决于你的网关）
OPENAI_CHAT_MODEL=gpt-4o-mini
# 向量模型名（如 text-embedding-3-small）
OPENAI_EMBEDDING_MODEL=text-embedding-3-small

# ===== 数据库（与 docker-compose.yml 保持一致）=====
DB_URL=jdbc:mysql://localhost:3306/agent_platform?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
DB_USERNAME=agent
DB_PASSWORD=agent123

# ===== Redis（与 docker-compose.yml 保持一致）=====
REDIS_HOST=localhost
REDIS_PORT=6379
```

- [ ] **Step 3: 起容器并验证健康**

Run:
```bash
cd agent-platform && docker compose up -d && sleep 15 && docker compose ps
```
Expected: `agent-mysql` 与 `agent-redis` 两个容器 `STATUS` 含 `healthy`。

Run（验证 Redis 向量能力存在）:
```bash
docker compose exec redis-stack redis-cli MODULE LIST | grep -i search
```
Expected: 输出含 `search`（RediSearch 模块已加载）。

- [ ] **Step 4: 提交**

```bash
cd /Users/liurongjie/my-brain-extension
git add agent-platform/docker-compose.yml agent-platform/.env.example
git commit -m "搭建本地基础设施: MySQL + Redis Stack 编排与配置模板"
```

---

### Task 2: Spring Boot 工程脚手架

**Files:**
- Create: `agent-platform/backend/pom.xml`
- Create: `agent-platform/backend/src/main/java/com/agentplatform/AgentPlatformApplication.java`
- Create: `agent-platform/backend/src/main/resources/application.yml`

**Interfaces:**
- Produces: 可构建的 Maven 工程；`AgentPlatformApplication` 启动类；`application.yml` 提供数据源/JPA/Flyway/OpenAI 配置，全部从环境变量读取并带默认值。

- [ ] **Step 1: 写 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.1</version>
    <relativePath/>
  </parent>

  <groupId>com.agentplatform</groupId>
  <artifactId>agent-platform-backend</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <name>agent-platform-backend</name>

  <properties>
    <java.version>17</java.version>
    <spring-ai.version>1.0.0</spring-ai.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-bom</artifactId>
        <version>${spring-ai.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>com.mysql</groupId>
      <artifactId>mysql-connector-j</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-mysql</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <optional>true</optional>
    </dependency>

    <!-- test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>mysql</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: 写启动类 AgentPlatformApplication.java**

```java
package com.agentplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AgentPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentPlatformApplication.class, args);
    }
}
```

- [ ] **Step 3: 写 application.yml**

```yaml
server:
  port: 8080

spring:
  application:
    name: agent-platform
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/agent_platform?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true}
    username: ${DB_USERNAME:agent}
    password: ${DB_PASSWORD:agent123}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:dummy-key}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      chat:
        options:
          model: ${OPENAI_CHAT_MODEL:gpt-4o-mini}
      embedding:
        options:
          model: ${OPENAI_EMBEDDING_MODEL:text-embedding-3-small}

logging:
  level:
    org.springframework.ai: INFO
```

- [ ] **Step 4: 生成 Maven Wrapper 并验证构建**

Run:
```bash
cd agent-platform/backend && mvn -N wrapper:wrapper -Dmaven=3.9.9 && ./mvnw -q -DskipTests compile
```
Expected: 构建成功，无报错（首次会下载依赖）。

- [ ] **Step 5: 提交**

```bash
cd /Users/liurongjie/my-brain-extension
git add agent-platform/backend/pom.xml agent-platform/backend/mvnw agent-platform/backend/mvnw.cmd agent-platform/backend/.mvn agent-platform/backend/src/main
git commit -m "初始化 Spring Boot 后端脚手架与配置"
```

---

### Task 3: 通用层（统一返回 + 业务异常 + 全局异常处理）

**Files:**
- Create: `agent-platform/backend/src/main/java/com/agentplatform/common/ApiResponse.java`
- Create: `agent-platform/backend/src/main/java/com/agentplatform/common/BusinessException.java`
- Create: `agent-platform/backend/src/main/java/com/agentplatform/common/GlobalExceptionHandler.java`
- Test: `agent-platform/backend/src/test/java/com/agentplatform/common/ApiResponseTest.java`
- Test: `agent-platform/backend/src/test/java/com/agentplatform/common/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces:
  - `ApiResponse<T>`：`record(int code, String message, T data)`；静态 `ok(T)`（code=0）、`error(int,String)`。
  - `BusinessException extends RuntimeException`：构造 `(int code, String message)`，`getCode()`。
  - `GlobalExceptionHandler`：`@RestControllerAdvice`，`handleBusiness(BusinessException)→ApiResponse<Void>`、`handle(Exception)→ApiResponse<Void>`（HTTP 500）。

- [ ] **Step 1: 写失败测试 ApiResponseTest.java**

```java
package com.agentplatform.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void ok_wraps_data_with_zero_code() {
        ApiResponse<String> r = ApiResponse.ok("hello");
        assertThat(r.code()).isEqualTo(0);
        assertThat(r.message()).isEqualTo("ok");
        assertThat(r.data()).isEqualTo("hello");
    }

    @Test
    void error_carries_code_and_message_with_null_data() {
        ApiResponse<Void> r = ApiResponse.error(404, "not found");
        assertThat(r.code()).isEqualTo(404);
        assertThat(r.message()).isEqualTo("not found");
        assertThat(r.data()).isNull();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd agent-platform/backend && ./mvnw -q -Dtest=ApiResponseTest test`
Expected: 编译失败 / FAIL（`ApiResponse` 不存在）。

- [ ] **Step 3: 写实现 ApiResponse.java**

```java
package com.agentplatform.common;

public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd agent-platform/backend && ./mvnw -q -Dtest=ApiResponseTest test`
Expected: PASS。

- [ ] **Step 5: 写失败测试 GlobalExceptionHandlerTest.java**

```java
package com.agentplatform.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void business_exception_maps_to_its_code_and_message() {
        ApiResponse<Void> r = handler.handleBusiness(new BusinessException(40001, "bad agent"));
        assertThat(r.code()).isEqualTo(40001);
        assertThat(r.message()).isEqualTo("bad agent");
    }

    @Test
    void generic_exception_maps_to_500() {
        ApiResponse<Void> r = handler.handle(new RuntimeException("boom"));
        assertThat(r.code()).isEqualTo(500);
        assertThat(r.message()).isEqualTo("boom");
    }
}
```

- [ ] **Step 6: 运行测试确认失败**

Run: `cd agent-platform/backend && ./mvnw -q -Dtest=GlobalExceptionHandlerTest test`
Expected: 编译失败（`BusinessException`/`GlobalExceptionHandler` 不存在）。

- [ ] **Step 7: 写实现 BusinessException.java 与 GlobalExceptionHandler.java**

`BusinessException.java`:
```java
package com.agentplatform.common;

public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
```

`GlobalExceptionHandler.java`:
```java
package com.agentplatform.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException e) {
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handle(Exception e) {
        return ApiResponse.error(500, e.getMessage());
    }
}
```

- [ ] **Step 8: 运行测试确认通过**

Run: `cd agent-platform/backend && ./mvnw -q -Dtest=GlobalExceptionHandlerTest test`
Expected: PASS。

- [ ] **Step 9: 提交**

```bash
cd /Users/liurongjie/my-brain-extension
git add agent-platform/backend/src/main/java/com/agentplatform/common agent-platform/backend/src/test/java/com/agentplatform/common
git commit -m "添加通用层: 统一返回包装与全局异常处理"
```

---

### Task 4: 数据库迁移（Flyway 建全部表）+ Testcontainers 集成测试基类

**Files:**
- Create: `agent-platform/backend/src/main/resources/db/migration/V1__init_schema.sql`
- Create: `agent-platform/backend/src/test/java/com/agentplatform/IntegrationTestBase.java`
- Test: `agent-platform/backend/src/test/java/com/agentplatform/migration/FlywayMigrationTest.java`

**Interfaces:**
- Produces:
  - 全部业务表（见设计文档第三节）：`agent`、`agent_knowledge_base`、`agent_tool`、`knowledge_base`、`document`、`tool`、`conversation`、`message`。后续计划的 JPA 实体映射到这些表。
  - `IntegrationTestBase`：抽象基类，`@SpringBootTest` + Testcontainers MySQL，注入数据源与测试用 `spring.ai.openai.api-key`。后续所有需要 Spring 上下文的测试都继承它。

- [ ] **Step 1: 写失败测试 FlywayMigrationTest.java**

```java
package com.agentplatform.migration;

import com.agentplatform.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void all_core_tables_exist_after_migration() {
        for (String table : new String[]{
                "agent", "agent_knowledge_base", "agent_tool",
                "knowledge_base", "document", "tool",
                "conversation", "message"}) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = DATABASE() AND table_name = ?",
                    Integer.class, table);
            assertThat(count).as("table %s should exist", table).isEqualTo(1);
        }
    }
}
```

- [ ] **Step 2: 写测试基类 IntegrationTestBase.java**

```java
package com.agentplatform;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class IntegrationTestBase {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0").withDatabaseName("agent_platform");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // 测试不打真实模型网络，但需要非空 key 让自动配置创建 bean
        registry.add("spring.ai.openai.api-key", () -> "test-key");
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `cd agent-platform/backend && ./mvnw -q -Dtest=FlywayMigrationTest test`
Expected: FAIL（无 V1 迁移脚本，Flyway 无表，断言 count=0）。

- [ ] **Step 4: 写迁移脚本 V1__init_schema.sql**

```sql
-- Agent 配置
CREATE TABLE agent (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    description   VARCHAR(512),
    avatar        VARCHAR(512),
    system_prompt TEXT,
    model         VARCHAR(128) NOT NULL,
    temperature   DOUBLE       NOT NULL DEFAULT 0.7,
    max_tokens    INT          NOT NULL DEFAULT 2048,
    top_p         DOUBLE       NOT NULL DEFAULT 1.0,
    agent_type    VARCHAR(32)  NOT NULL DEFAULT 'chat',
    enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 知识库
CREATE TABLE knowledge_base (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    embedding_model VARCHAR(128) NOT NULL,
    chunk_size      INT          NOT NULL DEFAULT 800,
    chunk_overlap   INT          NOT NULL DEFAULT 100,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 文档(元数据;向量在 Redis)
CREATE TABLE document (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id       BIGINT       NOT NULL,
    filename    VARCHAR(512) NOT NULL,
    file_type   VARCHAR(32),
    status      VARCHAR(32)  NOT NULL DEFAULT 'pending',
    chunk_count INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_document_kb FOREIGN KEY (kb_id) REFERENCES knowledge_base (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 工具定义(HTTP 接口型)
CREATE TABLE tool (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    name               VARCHAR(128) NOT NULL,
    description        VARCHAR(1024) NOT NULL,
    method             VARCHAR(16)  NOT NULL,
    url                VARCHAR(1024) NOT NULL,
    headers_json       TEXT,
    params_schema_json TEXT,
    enabled            TINYINT(1)   NOT NULL DEFAULT 1,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Agent ↔ 知识库
CREATE TABLE agent_knowledge_base (
    agent_id BIGINT NOT NULL,
    kb_id    BIGINT NOT NULL,
    PRIMARY KEY (agent_id, kb_id),
    CONSTRAINT fk_akb_agent FOREIGN KEY (agent_id) REFERENCES agent (id),
    CONSTRAINT fk_akb_kb    FOREIGN KEY (kb_id) REFERENCES knowledge_base (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Agent ↔ 工具
CREATE TABLE agent_tool (
    agent_id BIGINT NOT NULL,
    tool_id  BIGINT NOT NULL,
    PRIMARY KEY (agent_id, tool_id),
    CONSTRAINT fk_at_agent FOREIGN KEY (agent_id) REFERENCES agent (id),
    CONSTRAINT fk_at_tool  FOREIGN KEY (tool_id) REFERENCES tool (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 会话
CREATE TABLE conversation (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id   BIGINT       NOT NULL,
    user_id    VARCHAR(128) NOT NULL,
    title      VARCHAR(256),
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_conv_agent FOREIGN KEY (agent_id) REFERENCES agent (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 消息
CREATE TABLE message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT      NOT NULL,
    role            VARCHAR(16) NOT NULL,
    content         MEDIUMTEXT,
    tool_calls_json MEDIUMTEXT,
    token_usage     INT,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_msg_conv FOREIGN KEY (conversation_id) REFERENCES conversation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_document_kb ON document (kb_id);
CREATE INDEX idx_conversation_agent ON conversation (agent_id);
CREATE INDEX idx_conversation_user ON conversation (user_id);
CREATE INDEX idx_message_conv ON message (conversation_id);
```

- [ ] **Step 5: 运行测试确认通过**

Run: `cd agent-platform/backend && ./mvnw -q -Dtest=FlywayMigrationTest test`
Expected: PASS（需本机 Docker 可用，Testcontainers 会拉起 MySQL）。

- [ ] **Step 6: 提交**

```bash
cd /Users/liurongjie/my-brain-extension
git add agent-platform/backend/src/main/resources/db/migration agent-platform/backend/src/test/java/com/agentplatform/IntegrationTestBase.java agent-platform/backend/src/test/java/com/agentplatform/migration
git commit -m "添加 Flyway 全表迁移与 Testcontainers 集成测试基类"
```

---

### Task 5: 模型配置（OpenAI 兼容 ChatModel/EmbeddingModel 就位）

**Files:**
- Create: `agent-platform/backend/src/main/java/com/agentplatform/infra/ModelConfig.java`
- Test: `agent-platform/backend/src/test/java/com/agentplatform/infra/ModelConfigTest.java`

**Interfaces:**
- Consumes: Spring AI OpenAI 自动配置（由 `spring-ai-starter-model-openai` + `application.yml` 提供）。
- Produces: 验证容器内存在 `ChatModel` 与 `EmbeddingModel` bean，供后续 orchestrator/rag 注入。`ModelConfig` 作为 infra 包的占位配置类（含说明注释），后续计划在此扩展自定义封装。

- [ ] **Step 1: 写失败测试 ModelConfigTest.java**

```java
package com.agentplatform.infra;

import com.agentplatform.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ModelConfigTest extends IntegrationTestBase {

    @Autowired(required = false)
    ChatModel chatModel;

    @Autowired(required = false)
    EmbeddingModel embeddingModel;

    @Test
    void openai_compatible_models_are_wired() {
        assertThat(chatModel).as("ChatModel bean").isNotNull();
        assertThat(embeddingModel).as("EmbeddingModel bean").isNotNull();
    }
}
```

- [ ] **Step 2: 运行测试确认失败或通过的前提**

Run: `cd agent-platform/backend && ./mvnw -q -Dtest=ModelConfigTest test`
Expected: 若 `infra` 包尚不存在编译目标会 FAIL；继续 Step 3 落 `ModelConfig` 占位类后再跑。

- [ ] **Step 3: 写 ModelConfig.java**

```java
package com.agentplatform.infra;

import org.springframework.context.annotation.Configuration;

/**
 * Infra model configuration anchor.
 *
 * <p>Chat/Embedding models are provided by Spring AI's OpenAI auto-configuration
 * (spring-ai-starter-model-openai) and point to an OpenAI-compatible gateway via
 * {@code spring.ai.openai.base-url}. This class is the extension point for later
 * plans (custom retry/timeout, observation, prompt defaults).
 */
@Configuration
public class ModelConfig {
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd agent-platform/backend && ./mvnw -q -Dtest=ModelConfigTest test`
Expected: PASS（自动配置以 `test-key` 创建 bean，启动期不发网络请求）。

- [ ] **Step 5: 提交**

```bash
cd /Users/liurongjie/my-brain-extension
git add agent-platform/backend/src/main/java/com/agentplatform/infra agent-platform/backend/src/test/java/com/agentplatform/infra
git commit -m "接入 OpenAI 兼容模型配置并验证 bean 装配"
```

---

### Task 6: 健康检查接口

**Files:**
- Create: `agent-platform/backend/src/main/java/com/agentplatform/health/HealthController.java`
- Test: `agent-platform/backend/src/test/java/com/agentplatform/health/HealthControllerTest.java`

**Interfaces:**
- Consumes: `ApiResponse`（Task 3）。
- Produces: `GET /api/health` → `ApiResponse<Map<String,String>>`，`data.status = "UP"`。

- [ ] **Step 1: 写失败测试 HealthControllerTest.java**

```java
package com.agentplatform.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void health_returns_up() throws Exception {
        mvc.perform(get("/api/health"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0))
           .andExpect(jsonPath("$.data.status").value("UP"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd agent-platform/backend && ./mvnw -q -Dtest=HealthControllerTest test`
Expected: FAIL（`HealthController` 不存在）。

- [ ] **Step 3: 写实现 HealthController.java**

```java
package com.agentplatform.health;

import com.agentplatform.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "UP"));
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd agent-platform/backend && ./mvnw -q -Dtest=HealthControllerTest test`
Expected: PASS。

- [ ] **Step 5: 跑全部测试确认整体绿**

Run: `cd agent-platform/backend && ./mvnw -q test`
Expected: 所有测试通过（需 Docker 给 Testcontainers）。

- [ ] **Step 6: 提交**

```bash
cd /Users/liurongjie/my-brain-extension
git add agent-platform/backend/src/main/java/com/agentplatform/health agent-platform/backend/src/test/java/com/agentplatform/health
git commit -m "添加健康检查接口 /api/health"
```

---

## Self-Review（对照设计文档）

- **基础设施**：docker-compose 起 MySQL + Redis Stack（Task 1）✅；RediSearch 模块校验已含。Redis 向量库的 Spring 依赖与索引创建推迟到计划 03（因其自动配置启动期会连 Redis），foundation 不引入，避免 @SpringBootTest 依赖 Redis。
- **数据模型**：设计第三节全部 8 张表在 V1 脚本建好（Task 4）✅。
- **通用层 / 错误处理**：统一返回 + 全局异常（Task 3、设计第九节）✅。
- **模型**：OpenAI 兼容 base-url 配置 + bean 校验（Task 5）✅。
- **测试策略**：单测（common 纯单测）+ Testcontainers 集成（MySQL 迁移）（Task 4/5，设计第十节）✅；模型调用不打真实网络 ✅。
- **占位符扫描**：无 TBD/TODO；每个代码步骤含完整代码 ✅。
- **类型一致性**：`ApiResponse.ok/error`、`BusinessException(int,String)`、`HealthController` 返回 `ApiResponse<Map<String,String>>` 在测试与实现间一致 ✅。
- **超出 foundation 范畴的内容**（业务实体、SSE、RAG、工具、前端）均在后续计划，符合路线图边界。
