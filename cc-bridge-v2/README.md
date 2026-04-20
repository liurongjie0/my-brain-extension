# Claude Code → Langfuse Bridge v2

实时监听 Claude Code 会话日志，以消息级粒度推送到本地 Langfuse，实现会话可视化监控与分析。

## 特性

- **消息级粒度**：每条消息独立为 observation，thinking、tool_use、tool_result、attachment 等全部保留
- **增量同步**：基于文件行数偏移量，重启不重复推送
- **双保险监听**：`fs.watch` 实时感知 + 定时轮询兜底
- **零依赖**：纯 Node.js 原生模块，无需 npm install
- **完整元数据**：skill listing、command permissions、file-history-snapshot、system 消息均保留

## 快速开始

```bash
cd ~/my-brain-extension/cc-bridge-v2

# 1. 配置 config.json（填入 Langfuse API Key）
# 2. 启动
node bridge.js
```

## 配置

编辑 `config.json`：

```json
{
  "langfuse": {
    "host": "http://localhost:3000",
    "publicKey": "pk-lf-...",
    "secretKey": "sk-lf-..."
  },
  "userId": "liurongjie",
  "watchDir": "~/.claude/projects",
  "pollIntervalMs": 2000
}
```

| 字段 | 说明 |
|------|------|
| `langfuse.host` | Langfuse 服务地址 |
| `langfuse.publicKey` | Langfuse Project Public Key |
| `langfuse.secretKey` | Langfuse Project Secret Key |
| `userId` | Langfuse 中显示的用户标识 |
| `watchDir` | Claude Code 会话日志目录 |
| `pollIntervalMs` | 轮询间隔（毫秒） |

## Observation 类型

| name | 来源 | 说明 |
|------|------|------|
| `user-input` | user 消息 | 用户输入的文本 |
| `thinking` | assistant 消息 | AI 的思考过程 |
| `tool:{name}` | assistant 消息 | 工具调用（如 tool:Bash、tool:Read） |
| `tool-result` | user 消息 | 工具执行结果 |
| `response` | assistant 消息 | AI 最终回复（GENERATION 类型） |
| `intermediate-response` | assistant 消息 | AI 中间回复（继续调用工具） |
| `attachment:{type}` | attachment 消息 | skill listing、command permissions 等 |
| `skill-invocation` | user isMeta | skill 调用参数 |
| `system` | system 消息 | 系统上下文 |
| `file-history-snapshot` | snapshot 消息 | 文件追踪状态 |

## 文件说明

```
.
├── bridge.js      # 核心桥接脚本
├── config.json    # 用户配置（API Key、watch 目录等）
├── state.json     # 增量同步状态（自动维护）
└── README.md      # 本文档
```

## 状态管理

`state.json` 自动记录每个 jsonl 文件的同步进度：

```json
{
  "offsets": {
    "-Users-liurongjie-my-brain-extension/session-id.jsonl": {
      "lines": 281,
      "mtime": 1776604456000
    }
  },
  "sessions": {
    "session-id": { "createdAt": 1776606038422 }
  }
}
```

- 重置 offsets（重新推送历史数据）：`state.offsets = {}`
- sessions 保留避免重复创建 trace

## 数据流

```
Claude Code CLI
    ↓ 写入
~/.claude/projects/**/*.jsonl
    ↓ fs.watch / 轮询
bridge.js (解析 → 构建 observation)
    ↓ HTTP API
Langfuse (http://localhost:3000)
```

## 注意事项

1. **Langfuse 服务必须已启动**：`docker compose up -d`（在 `~/langfuse` 目录）
2. **首次扫描会处理所有历史数据**：根据 jsonl 文件数量，可能需要几秒到几十秒
3. **file-history-snapshot 无 timestamp**：使用当前时间作为 fallback
4. **Langfuse trace API 有 observation 数量限制**：大量 observation 的 trace 建议通过 Langfuse Web UI 查看

## License

MIT
