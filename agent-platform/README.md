# Agent 平台

面向团队内部的 Agent 平台：用户选 Agent 流式对话，管理员配置 Agent / 知识库 / 工具。支持知识问答(RAG)、工具调用(Function Calling)、多步骤任务(ReAct)。

- 设计文档：`docs/superpowers/specs/2026-06-20-agent-platform-design.md`
- 实现计划：`docs/superpowers/plans/2026-06-20-agent-platform-*.md`

## 技术栈

- 后端：Spring Boot 3.4 + Spring AI 1.0（OpenAI 兼容接口）+ JPA + MySQL + Flyway + Redis Stack(向量库)
- 前端：Vue3 + Vite + Element Plus + Pinia
- 基础设施：本地 docker-compose（MySQL + Redis Stack）

## 启动步骤

### 1. 起基础设施

```bash
cd agent-platform
docker compose up -d        # mysql:3306, redis-stack:6379, RedisInsight:8001
```

### 2. 配置环境变量

复制 `.env.example` 为 `.env` 并填写（或直接 export 环境变量）。必填模型相关：

```
OPENAI_BASE_URL=...     # OpenAI 兼容网关地址
OPENAI_API_KEY=...      # API Key
OPENAI_CHAT_MODEL=gpt-4o-mini
OPENAI_EMBEDDING_MODEL=text-embedding-3-small
```

数据库/Redis 默认值与 docker-compose 一致，无需改动。

### 3. 起后端

```bash
cd agent-platform/backend
# 需 JDK 17（本机 openjdk@17 为 keg-only）
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
# 从 .env 加载环境变量后启动（或用 IDE 配置环境变量）
set -a; source ../.env; set +a
./mvnw spring-boot:run
```

后端启动在 `http://localhost:8080`，健康检查 `GET /api/health`。

### 4. 起前端

```bash
cd agent-platform/frontend
npm install
npm run dev     # http://localhost:5173 ，Vite 代理 /api 到 8080
```

浏览器打开 `http://localhost:5173`：
- `/chat` 用户对话端：选 Agent、流式对话、ReAct 执行过程折叠、RAG 引用
- `/admin/agents` 管理 Agent（提示词/模型/类型/绑定知识库与工具/启用）
- `/admin/knowledge` 知识库与文档上传、检索测试
- `/admin/tools` HTTP 工具配置与测试
- `/admin/conversations` 会话审计

## 测试

```bash
# 后端（需 Docker，Testcontainers 起 MySQL + Redis Stack）
cd agent-platform/backend && ./mvnw test

# 前端
cd agent-platform/frontend && npm run test
```

## 说明与边界

- 认证暂未实现；`/api/admin/**` 已统一前缀，后续加拦截器即可。
- 文档第一版仅支持纯文本/Markdown（UTF-8）；原文上传后内存暂存并异步处理（`rag.auto-process`，默认开启）。
- 工具为 HTTP 接口型（method/url/headers/参数 schema），无需写代码即可配置。
- ReAct 多步骤上限 `MAX_STEPS=8`，每步通过 SSE `step` 事件推送并落库轨迹。
