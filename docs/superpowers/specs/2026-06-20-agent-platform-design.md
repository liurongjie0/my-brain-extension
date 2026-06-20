# Agent 平台设计文档

- 日期：2026-06-20
- 状态：设计已确认，待实现计划

## 一、背景与定位

构建一个面向**团队内部**的 Agent 平台（内部工具型 Agent 集合）：

- **用户端**：用户从启用的 Agent 列表里选一个，对话解决问题。
- **管理后台**：管理员配置 Agent（提示词、模型参数、绑定知识库/工具、启用开关）、管理知识库与工具、审计会话。
- 第一版即覆盖三类能力：**知识问答 (RAG)**、**工具调用 (Function Calling)**、**多步骤任务 Agent (ReAct)**。
- 目标是**真实内部上线使用**，需考虑稳定性与部署；认证暂不做但预留入口。

### 关键决策

| 维度 | 选择 |
|---|---|
| 定位 | 内部工具型 Agent 集合（固定几个 Agent，管理员配权限+少量参数） |
| 目的 | 真实内部上线 |
| 能力 | RAG + 工具调用 + 多步骤 Agent，一次性全做（不分期） |
| 模型 | OpenAI 兼容接口（通过 base-url 指向兼容网关） |
| 认证 | 暂不做，但 `/api/admin/**` 统一前缀预留拦截器入口 |
| 后端 | Spring Boot + Spring AI Alibaba，模块化单体 |
| 前端 | Vue3 + Element Plus（用户对话页 + 管理后台两个路由区，单仓） |
| 向量库 | Redis Stack（RediSearch 向量检索），复用 Redis |
| 基础设施 | 本地 docker-compose：MySQL + Redis Stack |

## 二、系统架构

```
┌─────────────────────────────────────────────────────┐
│  前端 (Vue3 + Element Plus, 单仓双入口)                  │
│  ┌──────────────┐        ┌──────────────────────┐    │
│  │ 用户对话端 /chat │        │ 管理后台 /admin         │    │
│  │ 选Agent、流式对话 │        │ 配Agent/知识库/工具/查会话 │    │
│  └──────────────┘        └──────────────────────┘    │
└───────────────────────────┬─────────────────────────┘
                            │ HTTP / SSE(流式)
┌───────────────────────────▼─────────────────────────┐
│  后端 Spring Boot + Spring AI Alibaba (模块化单体)       │
│  ┌────────┐ ┌──────────┐ ┌────────┐ ┌────────────┐  │
│  │ chat   │ │ agent     │ │ rag    │ │ tool        │  │
│  │ 对话/流式 │ │ Agent配置  │ │ 知识库   │ │ 工具注册/执行 │  │
│  │ 会话历史 │ │ CRUD      │ │ 向量检索 │ │ FunctionCall │  │
│  └────────┘ └──────────┘ └────────┘ └────────────┘  │
│  ┌─────────────────────────────────────────────────┐│
│  │ orchestrator: 编排 RAG检索→工具调用→多步骤(ReAct)    ││
│  └─────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────┐│
│  │ infra: OpenAI兼容ChatModel/EmbeddingModel封装       ││
│  └─────────────────────────────────────────────────┘│
└──────────┬──────────────────────────┬───────────────┘
           │                          │
   ┌───────▼────────┐        ┌────────▼─────────┐
   │ MySQL          │        │ Redis Stack      │
   │ Agent配置/会话/  │        │ 向量检索(知识库)   │
   │ 消息/知识库元数据/ │        │ + 会话短期缓存     │
   │ 工具定义        │        │                  │
   └────────────────┘        └──────────────────┘
        全部本地 docker-compose 一键起
```

### 后端模块职责（单体内分包，互相通过接口调用）

- `chat`：接收对话请求，调 orchestrator，SSE 流式返回，落库会话/消息。
- `agent`：Agent 的增删改查（提示词、模型参数、绑定的知识库/工具、启用开关）。
- `rag`：知识库管理、文档切分、向量化、Redis 向量检索。
- `tool`：工具定义注册（HTTP 接口型工具）、参数 schema、执行调用。
- `orchestrator`：核心编排——根据 Agent 配置决定是否先 RAG 检索、是否带工具、是否走多步骤 ReAct 循环。
- `infra`：封装 OpenAI 兼容的 ChatModel / EmbeddingModel、Redis 向量操作。
- `common`：全局异常、统一返回包装、配置。

## 三、数据模型

### MySQL 表

```
agent                          -- Agent 配置
  id, name, description, avatar
  system_prompt                -- 系统提示词
  model                        -- 模型名 (如 gpt-4o-mini / qwen-max)
  temperature, max_tokens, top_p
  agent_type                   -- chat / rag / tool / react (决定编排走法)
  enabled                      -- 启用开关
  created_at, updated_at

agent_knowledge_base           -- Agent ↔ 知识库 多对多绑定
  agent_id, kb_id

agent_tool                     -- Agent ↔ 工具 多对多绑定
  agent_id, tool_id

knowledge_base                 -- 知识库
  id, name, description
  embedding_model              -- 用的 embedding 模型
  chunk_size, chunk_overlap    -- 切分参数
  created_at

document                       -- 知识库下的文档(元数据,内容向量在Redis)
  id, kb_id, filename, file_type
  status                       -- pending/processing/done/failed
  chunk_count
  created_at

tool                           -- 工具定义 (HTTP 接口型)
  id, name, description        -- description 给 LLM 看,决定何时调用
  method, url                  -- 调用方式
  headers_json                 -- 固定请求头(如鉴权)
  params_schema_json           -- 参数 JSON Schema (给 FunctionCalling)
  enabled
  created_at

conversation                   -- 会话
  id, agent_id, user_id        -- user_id 暂存匿名标识(预留认证)
  title
  created_at, updated_at

message                        -- 消息
  id, conversation_id
  role                         -- user/assistant/tool/system
  content
  tool_calls_json              -- 多步骤时记录工具调用轨迹
  token_usage
  created_at
```

### Redis Stack 数据

```
向量索引 (RediSearch):  kb:{kbId}:chunk:{chunkId}
  - HASH: { content, vector(FLOAT32[]), doc_id, kb_id }
  - 建 HNSW 向量索引，按 kb_id 过滤检索

会话短期缓存(可选):  conv:{convId}:context
  - 最近 N 轮对话缓存，减少 MySQL 读取
```

### 设计要点

- `agent_type` 字段驱动 orchestrator 编排：`chat`=纯对话，`rag`=先检索再答，`tool`=带工具单轮，`react`=多步骤循环。一个 Agent 也可同时绑知识库与工具。
- 文档**元数据**进 MySQL，**向量**进 Redis，职责分离。
- 工具用"HTTP 接口型"通用建模（method + url + 参数 schema），管理员配工具无需写代码；orchestrator 把它转成 Spring AI 的 FunctionCallback 注册给模型。

## 四、核心编排流程（orchestrator）

核心入口：`chat(agentId, conversationId, userMessage) -> SSE流`。

### 统一流程

```
1. 加载 Agent 配置 + 历史消息(MySQL)
2. 组装 messages: [system_prompt] + 历史 + 本轮user
3. 按 agent_type / 绑定情况决定增强:
   ├─ 绑了知识库 → 先做 RAG 检索, 把命中片段拼进 context
   ├─ 绑了工具   → 把工具转成 FunctionCallback 注册给 ChatModel
   └─ 都没有     → 纯对话
4. 调 ChatModel (流式)
5. 多步骤循环(若有工具且模型要调用):
   while 模型返回 tool_call 且 未超 maxSteps:
     执行工具(HTTP调用) → 结果回填 messages → 再调模型
6. SSE 逐 token 推前端, 同时累积完整回复
7. 落库: user消息 + assistant消息 + 工具调用轨迹
```

### 三类能力分别怎么走

**① RAG 检索（绑了知识库时）**
```
user问题 → embedding → Redis向量检索(按kb_id过滤, top-k)
  → 取回片段拼成 context 块
  → 塞进 system 或 user 消息: "参考资料:\n{片段}\n\n问题:{user}"
  → 正常调模型生成
```

**② 工具调用（绑了工具时）**
```
把每个 tool 的 (name, description, params_schema) 注册为 Spring AI FunctionCallback
  → 模型自行决定是否调用、传什么参数
  → 框架回调 HttpToolExecutor: 按 tool 的 method/url/headers 发HTTP请求
  → 把响应JSON返回给模型继续生成
```

**③ 多步骤 ReAct（agent_type=react）**
```
即②的循环版: 模型可连续多轮调工具
  → maxSteps 上限(如 8 步) 防死循环
  → 每步的 思考/工具调用/结果 都通过 SSE 推给前端做"执行过程可视化"
  → 全部轨迹存进 message.tool_calls_json
```

### 关键设计点

- **RAG + 工具可叠加**：一个 react Agent 既能检索知识库、又能调工具，orchestrator 不互斥。
- **Spring AI 原生能力**：工具调用用 Spring AI 的 FunctionCallback 机制，不自造轮子；多步骤循环优先用 Spring AI 的 tool calling 自动循环，必要时手动控制 maxSteps（实现时定）。
- **流式贯穿**：纯对话和 ReAct 的每一步都走 SSE，前端体验统一。
- **失败兜底**：工具调用失败 → 错误信息回填给模型自行决策；RAG 检索空 → 退化为纯对话并提示"未命中知识库"。

## 五、API 设计

后端 REST API（前缀 `/api`）：

```
== 用户对话端 ==
GET  /api/agents                    启用的 Agent 列表(供用户选)
POST /api/chat                      发起对话 → SSE 流式返回
       body: {agentId, conversationId?, message}
GET  /api/conversations?userId=     我的会话列表
GET  /api/conversations/{id}/messages   某会话历史

== 管理后台 ==
# Agent 配置
GET/POST/PUT/DELETE  /api/admin/agents          Agent CRUD
PUT  /api/admin/agents/{id}/bindings            绑定知识库/工具

# 知识库
GET/POST/DELETE  /api/admin/knowledge-bases     知识库 CRUD
POST /api/admin/knowledge-bases/{id}/documents  上传文档(触发切分+向量化)
GET  /api/admin/knowledge-bases/{id}/documents  文档列表(含处理状态)
DELETE .../documents/{docId}                    删文档(连带删向量)
POST /api/admin/knowledge-bases/{id}/retrieve   检索测试(调试用)

# 工具
GET/POST/PUT/DELETE  /api/admin/tools           工具 CRUD
POST /api/admin/tools/{id}/test                 工具调用测试

# 会话审计
GET  /api/admin/conversations                   全部会话(分页/筛选)
GET  /api/admin/conversations/{id}              查看某会话完整轨迹
```

## 六、前端页面（Vue3 + Element Plus，单仓两个路由区）

```
用户端 /chat
  - 左侧: Agent 列表(卡片/列表) + 我的历史会话
  - 右侧: 对话窗口
      · 流式逐字输出
      · ReAct 执行过程折叠展示(思考→调用工具X→结果)
      · RAG 回答下方显示"引用来源"

管理后台 /admin
  - /admin/agents       Agent 管理(表格 + 编辑抽屉: 提示词/模型参数/类型/绑定知识库&工具/启用开关)
  - /admin/knowledge    知识库管理(列表 + 文档上传 + 处理状态 + 检索测试框)
  - /admin/tools        工具管理(表单配 method/url/headers/参数schema + 测试按钮)
  - /admin/conversations 会话审计(列表 + 详情查看完整轨迹)
```

### 前端要点

- **流式用 SSE**（`text/event-stream`），前端用 `EventSource` 或 fetch-stream 接收；ReAct 中间步骤作为不同 event 类型推送（`token` / `step` / `done` / `error`）。
- **文档处理异步化**：上传后立即返回，后台线程做切分+向量化，前端轮询文档 `status`。
- **认证预留**：所有 `/api/admin/**` 走同一前缀，将来加拦截器即可统一加认证，现在放行；`userId` 暂用前端生成的匿名 ID。

## 七、工程结构

新建 `agent-platform/` 目录：

```
agent-platform/
├── backend/                      Spring Boot + Spring AI Alibaba
│   ├── src/main/java/.../
│   │   ├── chat/                 对话接口 + SSE
│   │   ├── agent/                Agent CRUD
│   │   ├── rag/                  知识库/切分/向量化/检索
│   │   ├── tool/                 工具注册/HTTP执行
│   │   ├── orchestrator/         核心编排
│   │   ├── infra/                ChatModel/EmbeddingModel/Redis向量封装
│   │   └── common/               异常/返回包装/配置
│   ├── src/main/resources/
│   │   ├── application.yml       OpenAI兼容 base-url/key、数据源、Redis
│   │   └── db/migration/         Flyway 建表脚本
│   └── pom.xml
├── frontend/                     Vue3 + Element Plus + Vite
│   ├── src/
│   │   ├── views/chat/           用户对话端
│   │   ├── views/admin/          管理后台
│   │   ├── api/                  接口封装(含SSE)
│   │   └── router/               /chat 与 /admin 路由
│   └── package.json
├── docker-compose.yml            mysql + redis-stack
├── .env.example                  OPENAI_BASE_URL/KEY、库密码等
└── README.md                     一键启动说明
```

## 八、Docker 部署

```yaml
services:
  mysql:        # 8.x, 初始化建库, 挂卷持久化
  redis-stack:  # redis/redis-stack, 含 RediSearch 向量能力, 暴露 6379 + 8001(可视化)
```

- 后端/前端开发期本地跑（热重载方便）；`.env` 配 `OPENAI_BASE_URL`、`OPENAI_API_KEY`、库连接。
- 沿用 hiking-bot 的 `.env.example` 标注风格（每项标注获取路径）。

## 九、错误处理

- 全局异常处理器 `@RestControllerAdvice`，统一返回 `{code, message, data}`。
- 模型调用：超时 + 有限重试；SSE 流中途出错推 `error` event，前端提示。
- 工具调用失败：错误信息回填给模型自行决策，并记进轨迹。
- 向量化失败：文档置 `failed` 状态，支持重试。

## 十、测试策略

- 后端单测：覆盖 orchestrator 编排分支（纯对话/RAG/工具/ReAct 各一条）、工具 HTTP 执行、向量检索；模型调用用 mock，不打真实 API。
- 集成测试：用 Testcontainers 起 MySQL + Redis Stack，跑 RAG 检索与持久化。
- 前端：关键交互（流式渲染、Agent 配置表单）做组件测试，不强求全覆盖。
- 遵循 TDD：先写测试再实现。
