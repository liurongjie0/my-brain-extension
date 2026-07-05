# Mastra 多 Agent 旅行规划 Demo 设计

日期：2026-07-06
仓库：`mastra-customer-support-demo`（与客服 demo 共用一个 Mastra 实例与 Studio）

## 目标

演示 Mastra 的多 agent 协作（supervisor / routing agent 模式）：一个路由 agent 根据对话内容，自动把任务分派给合适的**子 agent**、**工具**和 **workflow**，多轮协作直到给出完整结果。全部使用 mock 数据，不调用真实外部 API。

场景：周边徒步短途游规划。用户描述需求（目的地/天数/预算/出发地），路由 agent 协调交通、住宿、行程编排，产出完整方案；用户追加约束（如砍预算）时只重派相关子任务。

## 架构

### 路由 agent：`travelPlannerAgent`

Mastra supervisor 模式——子 agent 通过 `agents` 配置挂载后自动成为 `agent-<key>` 工具，由路由 agent 的 LLM 决定分派顺序与参数：

- `agents`：
  - `transportAgent` — 交通专家，持有 `searchTransportTool`，按出发地/目的地查 mock 车次并给出推荐。
  - `lodgingAgent` — 住宿专家，持有 `searchLodgingTool`，按目的地/预算筛选 mock 住宿。
- `workflows`：
  - `itineraryWorkflow` — 确定性行程编排：输入目的地、天数、已选交通/住宿 → 逐日行程 + 预算汇总。
- `tools`（直挂在路由 agent 上）：
  - `searchTrailsTool` — 查询 mock 徒步线路库（目的地、难度、亮点）。
  - `buildPackingListTool` — 按线路难度/季节生成装备清单。
- `memory`：`travelMemory`（working memory 模板记录偏好/预算/已定选项，支撑多轮修改）。
- 模型解析沿用现有模式：`TRAVEL_AGENT_MODEL` 环境变量覆盖，默认 `deepseek/deepseek-chat`。
- 子 agent 必须带 `description`（路由 agent 依赖它决定分派）；`defaultOptions.maxSteps` 放宽到 15 以容纳多轮分派。

### Domain 层：`src/domain/travel.ts`

纯函数 + mock 数据，与 `orders.ts`/`refunds.ts` 同风格：

- 3 条线路：莫干山、徽杭古道、四明山，各配 2-3 个交通选项（车次/时长/价格）与 2-3 个住宿选项（价位分档）。
- `searchTrails(query)` / `searchTransport(from, trailId)` / `searchLodging(trailId, budgetCents?)` / `buildPackingList(trailId, season?)` / `buildItinerary(params)`，全部确定性；未知线路抛错；预算过低时住宿返回空列表并附原因字段。

### Schema 层：`src/mastra/travel-schemas.ts`

旅行相关 zod schema 独立成文件，避免膨胀现有 `schemas.ts`。

### 注册

`src/mastra/index.ts` 增加 3 个 agent、1 个 workflow、travel 工具集、`travelMemory`，与现有客服资源并列，Studio 内可见。

## 数据流（典型多轮）

1. 用户："帮我规划周末两天莫干山徒步，预算 1500，从上海出发。"
2. `travelPlannerAgent`：`searchTrailsTool` 确认线路 → 分派 `agent-transportAgent` 查交通 → 分派 `agent-lodgingAgent` 按预算查住宿 → 调 `itineraryWorkflow` 汇总 → `buildPackingListTool` → 输出完整方案（含"分派过程"小结）。
3. 用户："预算砍到 800。" → 路由 agent 依据 memory 中已定选项，只重派 `lodgingAgent` 并重跑 `itineraryWorkflow`。

## 错误处理

- domain 未知目的地/线路抛错，工具透传错误信息；路由 agent instructions 要求信息不足时向用户追问、不得编造数据。
- 预算不足：`searchLodging` 返回空列表 + reason，lodgingAgent 向路由 agent 报告约束冲突，由路由 agent 与用户协商。

## 测试

- domain 纯函数单测（线路查询、预算过滤、行程编排、装备清单、抛错路径）。
- tools 单测：直接调用 `execute`，断言输出 schema 语义。
- workflow 单测：`createRunAsync` 跑通确定性路径（参照现有 refund workflow demo 用法）。
- agent 配置单测：model resolution（环境变量覆盖）；断言子 agent/工具/workflow 已挂载、子 agent 带 description。
- registry 测试扩展：新资源注册断言。
- 路由 LLM 的实际分派行为不做自动化测试（需要模型 key），由 demo 脚本 / Studio 人工验证。

## Demo 入口

- Studio：`npm run dev`，选 `travel-planner-agent` 聊天，观察工具调用面板中的分派轨迹。
- CLI：`npm run demo:travel`（需 `DEEPSEEK_API_KEY`），流式打印每次分派的 tool-call 事件与最终方案。
