# AI Agent Demos — LangChain vs LangGraph vs OpenAI Agents SDK

同一个业务场景（AI 研究助手流水线），用三个主流框架各实现一次，方便直观对比。

## 业务场景

**输入**：一个研究主题（如"多智能体框架"）  
**输出**：一份结构化 Markdown 报告

**流水线**：
1. **Researcher** — 从知识库检索相关资料
2. **Analyst** — 分析资料，提取关键观点、趋势和空白
3. **Writer** — 生成结构化 Markdown 报告

## 环境要求

- **LangChain / LangGraph**: Python 3.9+
- **OpenAI Agents SDK**: Python 3.10+

## 快速开始

```bash
# 1. 安装依赖
pip install -r requirements.txt

# 2. 配置 API Key
cp .env.example .env
# 编辑 .env 填入你的 API Key

# 3. 运行三个 demo
python -m demo_langchain.pipeline "多智能体框架"
python -m demo_langgraph.pipeline "多智能体框架"
python -m demo_openai_agents.pipeline "多智能体框架"  # 需 Python 3.10+
```

## 环境变量

| 变量 | 说明 | 用于 |
|------|------|------|
| `ANTHROPIC_API_KEY` | Claude / Kimi API Key | LangChain, LangGraph |
| `ANTHROPIC_BASE_URL` | 自定义 base_url（如 Kimi） | LangChain, LangGraph |
| `OPENAI_API_KEY` | OpenAI API Key | OpenAI Agents SDK |
| `OPENAI_BASE_URL` | OpenAI 兼容端点（可选） | OpenAI Agents SDK |

> 如果使用 **Kimi API**：`ANTHROPIC_API_KEY` 和 `ANTHROPIC_BASE_URL`（如 `https://api.kimi.com/coding/`）即可运行 LangChain 和 LangGraph demo。OpenAI Agents SDK 需要 OpenAI 兼容的 API。

## 项目结构

```
ai-agent-demos/
├── shared/                    # 共享模块
│   ├── data_store.py          # 模拟知识库检索
│   └── models.py              # Pydantic 数据模型
├── demo_langchain/
│   └── pipeline.py            # LCEL 链式实现
├── demo_langgraph/
│   └── pipeline.py            # 图编排实现
├── demo_openai_agents/
│   └── pipeline.py            # Handoff 模式实现
├── comparison.md              # 三框架对比分析
└── README.md
```

## 核心差异速览

| | LangChain | LangGraph | OpenAI Agents SDK |
|---|---|---|---|
| **编排方式** | `|` 操作符串联 Runnable | 显式 StateGraph（节点+边） | Agent + Runner + Handoff |
| **状态管理** | 隐式（通过链传递） | 显式 State（TypedDict） | 隐式（Agent 内部管理） |
| **条件分支** | 较繁琐 | 原生支持（条件边） | Handoff 自动路由 |
| **学习曲线** | 中等 | 较陡 | 平缓 |
| **代码量** | 最少（声明式） | 最多（显式定义） | 中等 |

详见 [comparison.md](comparison.md)。
