# Claude Code 系统提示词资源汇总

> 收集 Claude Code 系统提示词相关的公开资源，用于学习和优化自己的提示工程。

---

## 1. 社区收集的系统提示词仓库

**GitHub: Piebald-AI/claude-code-system-prompts**
- https://github.com/Piebald-AI/claude-code-system-prompts
- 专门收集和版本化管理 Claude Code 的系统提示词
- 可以 clone 下来直接查看真实系统提示词文本

---

## 2. 系统提示词变化分析

**Simon Willison — Changes in the system prompt between Claude Opus 4.6 and 4.7**
- https://simonwillison.net/2026/Apr/18/opus-system-prompt/
- 对比了 4.6 到 4.7 的系统提示词差异
- 关键变化：
  - **主动性增强**：细节不明确时先尝试，不要先问
  - **工具搜索协议**：声称"没有权限"前必须调用 tool_search
  - **简洁性指令**：明确指示保持回答简洁聚焦
  - **移除过时护栏**：如避免特定语气词等限制
  - **安全细化**：用上下文特定规则替代宽泛过滤

---

## 3. Prompt Learning 自动优化

**Arize/Phoenix — CLAUDE.md: Best Practices from Prompt Learning**
- https://arize.com/blog/claude-md-best-practices-learned-from-optimizing-claude-code-with-prompt-learning/
- 使用强化学习风格循环自动优化系统提示词
- 在 SWE Bench Lite 上取得 **+5%** 通用性能提升
- 针对特定仓库优化可达 **+11%**
- 关键发现：LLM evaluator 提供定性反馈（为什么失败）比单纯 pass/fail 更有效

---

## 4. 8 阶段编码技能 (/wizard)

**Dev.to — I Made Claude Code Think Before It Codes**
- https://dev.to/_vjk/i-made-claude-code-think-before-it-codes-heres-the-prompt-bf
- 结构化多阶段系统提示词，安装为 Claude Code skill
- 8 个阶段：
  1. 任务评估 — 读 CLAUDE.md，分类复杂度
  2. 调查研究 — Grep 验证 API 和关系
  3. 测试优先 — 写失败测试
  4. 最小实现 — 仅写通过测试的代码
  5. 回归验证 — 运行全量测试
  6. 文档更新 — 更新 changelog 和注释
  7. 对抗审查 — 以攻击者视角审查
  8. 质量门控 — 修复自动化审查发现

---

## 5. 25 种 Prompt 技巧测试

**DreamHost — We Tested 25 Popular Claude Prompt Techniques**
- https://www.dreamhost.com/blog/claude-prompt-engineering/
- 测试了 25 种流行的 Claude 提示工程技术
- Extended Thinking 在复杂规划任务上提升 **18%**

---

## 系统提示词核心结构（逆向总结）

| 模块 | 作用 |
|------|------|
| 身份与能力 | "You are Claude Code..." |
| 工具使用协议 | 何时调用、如何格式化、错误处理 |
| 主动性规则 | 细节不明时先尝试再问 |
| 代码规范 | 测试、安全、注释 |
| 交互风格 | 简洁、不重复总结 |
| 记忆系统 | MEMORY.md 读写 |
| 安全边界 | 允许/禁止的操作 |
