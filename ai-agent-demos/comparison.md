# LangChain vs LangGraph vs OpenAI Agents SDK — 对比分析

## 1. 代码量对比

| 框架 | 文件数 | 代码行数 | 核心编排代码行数 |
|------|--------|---------|-----------------|
| **LangChain (LCEL)** | 1 | ~130 | ~15 (chain = a \| b \| c) |
| **LangGraph** | 1 | ~150 | ~25 (State + Graph + Edge) |
| **OpenAI Agents SDK** | 1 | ~150 | ~20 (Agent定义 + Runner.run) |

## 1.1 实际运行验证

| 框架 | 状态 | 环境 |
|------|------|------|
| **LangChain** | ✅ 运行成功，生成完整 Markdown 报告 | Python 3.9 + Kimi API |
| **LangGraph** | ✅ 运行成功，生成完整 Markdown 报告 | Python 3.9 + Kimi API |
| **OpenAI Agents SDK** | ⚠️ 代码结构完整，需 Python 3.10+ | Python 3.10+ + OpenAI API |

LangChain 的 `|` 操作符最简洁，LangGraph 因为需要显式定义 State 和 Graph 所以代码最多。

## 2. 核心概念映射

| 概念 | LangChain | LangGraph | OpenAI Agents SDK |
|------|-----------|-----------|-------------------|
| **Agent** | `Runnable` (函数或 LLM 调用) | `Node` (图中的节点函数) | `Agent` (instructions + tools + model) |
| **编排** | `RunnableSequence` (`a \| b \| c`) | `StateGraph` + `Edge` | `Runner.run_sync()` + `Handoff` |
| **状态** | 隐式传递（每个 Runnable 的输入输出） | 显式 `State` (TypedDict) | Agent 内部管理 |
| **工具** | `@tool` 装饰器 | `@tool` 装饰器 | `@function_tool` 装饰器 |

## 3. 编排能力

### 3.1 顺序流

**LangChain**：最优雅
```python
pipeline = researcher | analyst | writer
result = pipeline.invoke({"topic": "xxx"})
```

**LangGraph**：显式但清晰
```python
workflow.add_edge("researcher", "analyst")
workflow.add_edge("analyst", "writer")
```

**OpenAI Agents SDK**：自动或手动
```python
# Handoff 自动路由
# 或手动串行：
result1 = Runner.run_sync(agent1, input)
result2 = Runner.run_sync(agent2, result1)
```

### 3.2 条件分支

**LangChain**：需要 `RunnableBranch`，较繁琐
```python
branch = RunnableBranch(
    (lambda x: x["score"] > 80, high_quality),
    (lambda x: x["score"] > 50, medium_quality),
    low_quality,
)
```

**LangGraph**：原生支持，最强大
```python
def should_continue(state):
    if state["quality"] > 80: return "end"
    return "revise"

workflow.add_conditional_edges("writer", should_continue)
```

**OpenAI Agents SDK**：通过 Handoff 自动决定
```python
Agent(
    handoffs=[specialist_a, specialist_b, specialist_c]
    # LLM 自动决定转接给谁
)
```

### 3.3 循环/迭代

**LangChain**：不原生支持，需要手动递归调用

**LangGraph**：原生支持（图的循环边）
```python
workflow.add_edge("reviewer", "writer")  # 审稿不通过，回写
workflow.add_edge("writer", "reviewer")  # 形成循环
```

**OpenAI Agents SDK**：Runner 内部自动循环（tool use loop）

## 4. 状态管理

| 框架 | 方式 | 优点 | 缺点 |
|------|------|------|------|
| **LangChain** | 隐式传递 | 代码简洁 | 调试困难，中间状态不可见 |
| **LangGraph** | 显式 State | 可检查、可恢复、时间旅行 | 代码更啰嗦 |
| **OpenAI Agents SDK** | Agent 内部 | 零配置 | 黑盒，难以干预 |

LangGraph 的显式状态管理是其最大优势——你可以：
- 在任意节点打断，检查中间结果
- 从断点恢复执行（checkpoint）
- 回溯到之前的节点重新执行

## 5. 可观测性

| 框架 | 追踪方式 | 可视化 |
|------|---------|--------|
| **LangChain** | LangSmith（需额外配置） | 链执行图 |
| **LangGraph** | LangSmith + 图可视化 | 状态图 + 执行路径 |
| **OpenAI Agents SDK** | 内置 Tracing（零配置） | Agent 运行轨迹 |

OpenAI Agents SDK 的追踪体验最好——开箱即用，不需要额外注册服务。

## 6. 模型灵活性

| 框架 | 支持模型 | 切换成本 |
|------|---------|---------|
| **LangChain** | 80+ 提供商 | 换一行代码：`ChatOpenAI` → `ChatAnthropic` |
| **LangGraph** | 80+ 提供商（继承 LangChain） | 同上 |
| **OpenAI Agents SDK** | OpenAI 优先，其他通过 litellm/any-llm | 配置较复杂 |

## 7. 选型建议

### 选 LangChain 如果：
- 你需要**快速搭建**顺序流水线
- 项目简单，不需要复杂状态管理
- 你已经熟悉 LangChain 生态

### 选 LangGraph 如果：
- 你需要**循环、条件分支、显式状态**
- 做**长期运行的 Agent**（需要断点续传）
- 需要**人机协作**（暂停/恢复/回溯）
- 你需要**可审计**的执行路径

### 选 OpenAI Agents SDK 如果：
- 你**主要用 OpenAI 模型**
- 做**多 Agent 分诊/转接**（客服、助手）
- 追求**最小代码量**和**零配置可观测性**
- 不想维护复杂的框架依赖

## 8. 我们的场景结论

在这个"研究助手流水线"场景中：

- **LangChain** 最简洁（15 行编排代码），适合顺序流
- **LangGraph** 最适合扩展——如果未来需要加"审稿→重写"循环，只需要加一条边
- **OpenAI Agents SDK** 的 Handoff 模式在这个场景中优势不明显（因为没有真正的多 Agent 协作决策），但如果把三个 Agent 改为并行的 specialist，它的自动路由会很有价值
