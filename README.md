# my-brain-extension

我的个人 AI 学习主仓库 —— 围绕 Claude Code 和 AI 辅助开发的一系列工具、笔记和实验的聚合入口。

## 理念

这个仓库作为我 AI 学习之旅的**中央索引**。每个模块都有自己独立的仓库，并在此通过 git submodule 链接，既能独立演进，又保持统一的导航入口。

## 模块

| 模块 | 说明 | 类型 |
|------|------|------|
| [cc-bridge-v2](./cc-bridge-v2) | Claude Code → Langfuse 观测桥接器。消息级粒度实时追踪，支持增量同步。 | 自有项目 |
| [claude-notes](./claude-notes) | 学习笔记：system prompt 研究、上下文工程模型、部署指南。 | 自有内容 |
| [claude-statusline](./claude-statusline) | Claude Code 自定义状态栏，显示模型信息、token 用量、速率限制。 | 子模块 |
| [superpowers](./superpowers) | 面向编码智能体的可组合技能方法论（TDD、调试、规划、代码审查等）。 | 子模块 |

## 快速开始

```bash
# 克隆并拉取所有子模块
git clone --recursive <本仓库地址>

# 或已克隆后初始化子模块
git submodule update --init --recursive

# 更新所有子模块到最新
git submodule update --remote
```

## 模块详情

### cc-bridge-v2

一个 Node.js 桥接服务，监听 Claude Code 的会话 JSONL 文件，将结构化观测数据转发到 Langfuse 实例。

**核心功能：**
- 消息级粒度（用户输入、助手思考、工具调用、工具结果）
- 增量同步与偏移量追踪
- 自动生成会话级别的 trace/span
- 支持附件、系统消息、快照等元数据

[→ 进入项目](./cc-bridge-v2)

### claude-notes

探索 Claude Code 内部机制和 AI 辅助工作流过程中积累的个人学习文档。

**涵盖主题：**
- Claude Code system prompt 结构与资源
- 上下文工程模型及其演进
- 自托管可观测性（Langfuse + Claude Code 部署）

[→ 浏览笔记](./claude-notes)

### claude-statusline *(子模块)*

> 原仓库：[daniel3303/ClaudeCodeStatusLine](https://github.com/daniel3303/ClaudeCodeStatusLine)

Claude Code 的精简状态栏，实时显示模型信息、token 用量百分比和速率限制状态。

[→ 进入子模块](./claude-statusline)

### superpowers *(子模块)*

> 原仓库：[obra/superpowers](https://github.com/obra/superpowers)

面向编码智能体的软件开发方法论，提供可组合技能：
- 系统化调试
- 测试驱动开发
- 子代理驱动开发
- 头脑风暴与规划
- 代码审查工作流

[→ 进入子模块](./superpowers)

## 贡献

各模块有独立的仓库和贡献指南，请查看对应模块的 README。

## 许可证

各模块保留其原始许可证，请查看子模块中的 LICENSE 文件。
