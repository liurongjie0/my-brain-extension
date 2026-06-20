# Agent 平台实现路线图（多份有序计划）

> 设计文档：`docs/superpowers/specs/2026-06-20-agent-platform-design.md`

本平台拆成 6 份有序计划，**按顺序执行**。每份产出"可运行、可测试"的软件，后一份依赖前一份。

| # | 计划文档 | 交付物（done 的标准） | 依赖 |
|---|---|---|---|
| 01 | `2026-06-20-agent-platform-01-foundation.md` | 后端工程能启动；docker-compose 起 MySQL+Redis Stack；Flyway 建好全部表；health 接口绿；common(统一返回/异常)有测试；infra 的 ChatModel/EmbeddingModel 配置就位 | — |
| 02 | `…-02-agent-chat.md`（执行完 01 后写） | Agent CRUD 接口；会话/消息持久化；纯对话 SSE 流式跑通；orchestrator 骨架（chat 分支） | 01 |
| 03 | `…-03-rag.md` | 知识库/文档 CRUD；上传→异步切分→embedding→存 Redis；检索测试接口；orchestrator 接入 RAG 增强（带引用来源） | 02 |
| 04 | `…-04-tools.md` | 工具 CRUD；HTTP 工具执行器；转 Spring AI ToolCallback 注册；单轮工具调用接入 orchestrator；工具测试接口 | 02 |
| 05 | `…-05-react-orchestration.md` | 多步骤 ReAct（maxSteps 循环）；SSE step 事件；轨迹落库；RAG+工具叠加；引入 Spring AI Alibaba graph | 03, 04 |
| 06 | `…-06-frontend.md` | Vue3+Element Plus；用户对话端（流式/引用/ReAct 过程）；管理后台（Agent/知识库/工具/会话审计） | 02–05 后端接口 |

## 全局技术基线（所有计划通用）

- **JDK 17**，构建工具 **Maven**
- **Spring Boot 3.4.x**
- **Spring AI BOM `1.0.0`**（核心：`spring-ai-starter-model-openai` 走 OpenAI 兼容 base-url；`spring-ai-starter-vector-store-redis` 走 RediSearch）
- **Spring AI Alibaba BOM `1.0.0.2`**（仅计划 05 的 graph 用到）
- **持久层**：Spring Data JPA + MySQL 8；**迁移**：Flyway
- **前端**：Vue3 + Vite + Element Plus + Pinia
- **测试**：JUnit5 + Mockito（单测，模型调用一律 mock）；Testcontainers **2.0.5**（集成测试起 MySQL + Redis Stack）

> **执行 01 时的实际经验（后续计划沿用）：**
> - 本机 Docker Engine 为 29.x（最低 API 1.44），Spring Boot 默认管理的 Testcontainers 1.20.x 不兼容（`/info` 返回 400）。已在 `pom.xml` 用 `<testcontainers.version>2.0.5</testcontainers.version>` + 显式导入 `testcontainers-bom` 解决。注意 TC 2.x 模块坐标加了 `testcontainers-` 前缀（如 `testcontainers-junit-jupiter`、`testcontainers-mysql`）。
> - `IntegrationTestBase` 采用**单例容器模式**（静态块 `start()`，全程共享），避免 Spring 上下文缓存与每类容器生命周期冲突。后续需要 Redis 的集成测试在此基类追加共享 redis-stack 容器即可。
> - openjdk@17 为 keg-only：所有 `mvn`/`mvnw` 命令需带 `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`。
- **配置**：敏感配置走环境变量/`.env`，沿用 hiking-bot 的 `.env.example` 标注风格
- **代码注释/命名用英文，文档与提交信息用中文**（遵循 CLAUDE.md）

## 写作节奏

为让后续计划吸收前面执行的实际经验，**逐份编写**：执行完计划 01 后再写 02，依此类推。本路线图随进展更新勾选状态。

- [x] 01 地基（完成：7 测试全绿，分支 feat/agent-platform-foundation）
- [x] 02 Agent 配置 + 纯对话（完成：15 测试全绿。经验：JPA 字段 `topP` 命名策略会错成 `topp`，NOT NULL 列需显式 `@Column`；ddl-auto 已改 none，靠存取往返测试保证映射）
- [x] 03 RAG（完成：21 测试全绿。经验：自定义 vectorStore bean 需排除 `RedisVectorStoreAutoConfiguration` 避免 bean 同名冲突；测试用 FakeEmbeddingModel 避免向量库初始化触发 embedding 网络调用；`rag.auto-process=false` 关闭测试中的异步处理保证确定性）
- [x] 04 工具（完成：25 测试全绿。HTTP 工具 + DynamicHttpToolCallback 适配 Spring AI ToolCallback，框架内置自动执行；Agent-工具绑定）
- [ ] 05 多步骤 ReAct + 编排完善
- [ ] 06 前端
