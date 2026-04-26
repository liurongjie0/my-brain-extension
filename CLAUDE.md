# CLAUDE.md

## 语言

所有交流使用中文。代码中的注释、变量名保持英文，但与用户对话、文档编写、提交信息均使用中文。

## 项目索引

- `hiking-bot/` — 徒步路线规划机器人（企业微信群聊交互）

## Hiking Bot 项目知识

### 配置管理

- 配置统一通过 `.env` 文件管理，项目根目录下复制 `.env.example` 为 `.env` 后填写
- `app/config.py` 使用 `python-dotenv` 加载 `.env`，优先级：环境变量 > .env 文件 > 默认值
- 企微相关 5 个配置（CORP_ID, AGENT_ID, SECRET, TOKEN, ENCODING_AES_KEY）为必填
- AI 相关配置为可选，无 AI Key 时回退到内置的 10 条上海周边经典路线库
- `.env.example` 中每项配置都标注了获取路径（如"企业微信管理后台 -> 我的企业 -> 企业ID"）

### 架构要点

- FastAPI + SQLite + 内存对话状态（TTL 24h）
- 交互模式：@机器人自然语言 → 信息不足时主动追问 → 生成路线卡片 + 日程 + 清单 → 用户确认后保存
- 路线生成：优先调用 AI API（JSON mode），失败或无 Key 时回退到内置路线库
- 企微消息：当前只实现了文本/markdown 发送，图文卡片待接入

### 本地测试

- `test_cli.py` 提供终端交互式测试，无需配置企微
- 输入 `reset` 重置当前对话状态，输入 `quit` 退出
