# Langfuse + Claude Code 自托管观测系统部署指南

> 本文档记录从零开始部署 Langfuse 并接入 Claude Code 工具调用追踪的完整过程，使用 WHY-HOW-WHAT 框架组织。

---

## WHY：为什么需要这套系统

### 1. 问题：Claude Code 是黑盒

Claude Code 的每次对话中，AI 会调用多种工具（Read、Edit、Bash、Grep 等），但：
- **本地没有完整 trace**：`~/.claude/sessions/` 只存元数据，`history.jsonl` 只记录用户输入
- **无法复盘**：一轮复杂 session 结束后，无法回看 AI 具体调用了哪些工具、以什么顺序、每次耗时多久
- **无法优化**：团队无法分析 "为什么这次 Claude 走了弯路"，无法沉淀最佳实践

### 2. 需求：团队级可观测性

| 需求 | 说明 |
|------|------|
| **不上云** | 代码和数据敏感，不能上传到 Braintrust SaaS 等第三方 |
| **自托管** | 部署在团队内网服务器或本地 |
| **可搜索** | 能按项目、时间、工具类型搜索历史 trace |
| **仪表盘** | 可视化展示：对话轮次、工具调用链、token 用量、耗时 |
| **开源** | 避免 vendor lock-in，可二次开发 |

### 3. 选型：Langfuse

对比后选择 **Langfuse**（Apache 2.0）：
- 完全开源，可 Docker 自托管
- 原生支持 trace/session/user 三级模型
- 社区有成熟的 Claude Code 桥接工具 `claude-langfuse-monitor`
- UI 支持时间线、嵌套 span、搜索过滤

对比方案：

| 方案 | 开源 | 自托管 | Claude Code 集成 | 重量 |
|------|------|--------|-------------------|------|
| Braintrust | ❌ 部分 | ⚠️ 仅 data plane | ✅ 官方插件 | 轻量 |
| Arize Phoenix | ✅ | ✅ | ❌ 需自研 | 极轻 |
| **Langfuse** | ✅ | ✅ | ✅ 社区桥接 | 中等 |
| claude-trace | ✅ | ✅ | ❌ 需手动 | 轻量 |

---

## WHAT：系统架构与组件

```
┌─────────────────────────────────────────────────────────────┐
│                     Claude Code CLI                         │
│  (用户交互层：输入命令 → AI 推理 → 工具调用 → 结果返回)       │
│                                                             │
│  ~/.claude/projects/        ← 项目级会话存储                  │
│  ~/.claude/sessions/*.json  ← 会话元数据                      │
└──────────────────────┬──────────────────────────────────────┘
                       │ 文件系统监控
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              claude-langfuse-monitor (桥接)                  │
│  - 监听 ~/.claude/ 目录变化                                  │
│  - 解析会话 JSON，提取：用户输入、AI回复、工具调用、耗时       │
│  - 转换为 Langfuse trace/observation 格式                    │
│  - 通过 HTTP API 推送到 Langfuse                             │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTP API
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              Langfuse (自托管，Docker Compose)               │
│                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ langfuse-web│  │langfuse-work│  │      PostgreSQL     │ │
│  │  (Web UI)   │  │   (worker)  │  │    (trace 元数据)    │ │
│  │  port 3000  │  │  port 3030  │  │      port 5432      │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
│                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │  ClickHouse │  │    Redis    │  │       MinIO         │ │
│  │  (trace 数据)│  │   (队列)    │  │   (文件/媒体存储)    │ │
│  │  port 8123  │  │  port 6379  │  │   port 9090/9091   │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 各组件职责

| 组件 | 职责 | 数据 |
|------|------|------|
| **langfuse-web** | Next.js Web UI + API | 无状态，不存数据 |
| **langfuse-worker** | 异步处理 trace 写入、聚合 | 消费 Redis 队列 |
| **PostgreSQL** | 用户、项目、配置、trace 元数据 | 持久化 |
| **ClickHouse** | trace 事件、observation、span 数据 | 持久化（主要存储） |
| **Redis** | 任务队列、缓存 | 临时 |
| **MinIO** | 文件上传、媒体附件 | 持久化 |

### Langfuse 数据模型

```
User (liurongjie)
└── Session (一次完整对话，如 30 分钟的 session)
    ├── Trace (一轮交互 / user turn)
    │   ├── Observation: generation (AI 回复)
    │   ├── Observation: tool_call (Read 文件)
    │   ├── Observation: tool_call (Edit 文件)
    │   └── Observation: tool_call (Bash 命令)
    ├── Trace (下一轮交互)
    └── Trace (...)
```

| 层级 | 对应 Claude Code |
|------|-----------------|
| **User** | 使用者（原硬编码为作者邮箱，已修复） |
| **Session** | 一次从启动到退出的完整对话 |
| **Trace** | 用户说一句 + AI 回复 + 工具调用的完整轮次 |
| **Observation** | 单个工具调用或 generation 事件 |

---

## HOW：部署操作步骤

### 前置条件

- macOS / Linux
- Docker + Docker Compose 已安装
- Node.js + npm 已安装

### Step 1：下载 Langfuse Docker Compose

```bash
mkdir -p ~/langfuse && cd ~/langfuse

# 下载官方 compose 文件
curl -fsSL -o docker-compose.yml \
  https://raw.githubusercontent.com/langfuse/langfuse/main/docker-compose.yml
```

### Step 2：生成安全凭据

```bash
cd ~/langfuse

cat > .env <<'EOF'
# Web UI 地址
NEXTAUTH_URL=http://localhost:3000

# 数据库连接
DATABASE_URL=postgresql://postgres:postgres@postgres:5432/postgres

# 安全密钥（必须修改！）
SALT=$(openssl rand -hex 16)
ENCRYPTION_KEY=$(openssl rand -hex 32)
NEXTAUTH_SECRET=$(openssl rand -hex 32)

# 各组件密码（生产环境必须修改默认值）
POSTGRES_PASSWORD=postgres
CLICKHOUSE_PASSWORD=clickhouse
REDIS_AUTH=myredissecret
MINIO_ROOT_PASSWORD=miniosecret
LANGFUSE_S3_EVENT_UPLOAD_SECRET_ACCESS_KEY=miniosecret
LANGFUSE_S3_MEDIA_UPLOAD_SECRET_ACCESS_KEY=miniosecret
LANGFUSE_S3_BATCH_EXPORT_SECRET_ACCESS_KEY=miniosecret

# 禁用 Langfuse 遥测（可选）
TELEMETRY_ENABLED=false
EOF
```

> ⚠️ **安全提示**：`.env` 文件中的 `ENCRYPTION_KEY` 和 `NEXTAUTH_SECRET` 必须生成随机值，生产环境所有密码都应替换为强密码。

### Step 3：启动服务

```bash
cd ~/langfuse
docker compose up -d
```

验证所有 6 个服务都 healthy：

```bash
docker compose ps
```

预期输出：
```
NAME                         STATUS                   PORTS
langfuse-clickhouse-1        Up (healthy)             127.0.0.1:8123->8123/tcp
langfuse-langfuse-web-1      Up                       0.0.0.0:3000->3000/tcp
langfuse-langfuse-worker-1   Up                       127.0.0.1:3030->3030/tcp
langfuse-minio-1             Up (healthy)             0.0.0.0:9090->9000/tcp
langfuse-postgres-1          Up (healthy)             127.0.0.1:5432->5432/tcp
langfuse-redis-1             Up (healthy)             127.0.0.1:6379->6379/tcp
```

### Step 4：初始化 Langfuse 账号

1. 浏览器打开 http://localhost:3000
2. 首次访问自动进入注册页面
3. 填写：邮箱、用户名、密码
4. 创建 Organization（如 `my-team`）和 Project（建议命名为 `claude-code`）

### Step 5：获取 API Key

进入 Project Settings → API Keys：
- 点击 "Create new API keys"
- 复制 `Public Key`（`pk-lf-...`）和 `Secret Key`（`sk-lf-...`）

### Step 6：安装桥接工具

```bash
npm install -g claude-langfuse-monitor
```

### Step 7：配置桥接工具

```bash
claude-langfuse config \
  --host http://localhost:3000 \
  --public-key pk-lf-YOUR_PUBLIC_KEY \
  --secret-key sk-lf-YOUR_SECRET_KEY
```

配置保存在 `~/.claude-langfuse/config.json`。

### Step 8：修复 userId（重要！）

桥接工具源码中 `userId` 被**硬编码**为作者邮箱 `michael@oboyle.co`，需要手动修改为实际使用者：

```bash
# 找到源码位置（全局安装路径）
SOURCE_FILE="/opt/homebrew/lib/node_modules/claude-langfuse-monitor/index.js"

# 确认硬编码位置
grep -n "userId:" "$SOURCE_FILE"
# 输出示例：266:          userId: 'michael@oboyle.co',

# 替换为实际使用者
sed -i.bak "s/userId: 'michael@oboyle.co'/userId: 'liurongjie'/" "$SOURCE_FILE"
```

> 如果有多人使用，目前需要各自修改源码或等社区修复。已记录为 [TODO] 后续可改用环境变量传入。

### Step 9：启动监控

```bash
# 后台模式，监听 ~/.claude/ 目录变化
claude-langfuse start -d -q
```

首次启动会自动处理过去 24 小时的历史会话。

验证状态：

```bash
claude-langfuse status
```

### Step 10：查看数据

浏览器打开 http://localhost:3000，进入你的 project：

- **Traces**：每次 user turn 的完整 trace，含工具调用链
- **Sessions**：按 session 聚合的对话概览
- **Users**：按用户聚合的所有 trace
- **Dashboards**：token 用量、耗时统计等图表

---

## 踩坑记录

### 1. Docker 下载超时（国内网络）

**现象**：`docker compose up -d` 拉取镜像时超时或失败。

**解决**：配置 Docker Hub 国内镜像加速，或手动 `docker pull` 后重试。本次实际使用 Homebrew 安装 Docker Desktop 解决。

### 2. userId 硬编码为作者邮箱

**现象**：Langfuse UI 中所有 trace 的 User 显示为 `michael@oboyle.co`。

**根因**：`claude-langfuse-monitor` 源码 `index.js:266` 硬编码了 `userId: 'michael@oboyle.co'`。

**解决**：手动修改源码中的 `userId` 为实际使用者标识。后续可考虑向社区提 PR 支持通过环境变量/CLI 参数传入。

### 3. 端口冲突

Langfuse 默认使用以下端口，确保没有被占用：

| 端口 | 服务 |
|------|------|
| 3000 | Langfuse Web UI |
| 3030 | Langfuse Worker |
| 5432 | PostgreSQL |
| 6379 | Redis |
| 8123/9000 | ClickHouse |
| 9090/9091 | MinIO |

如需修改，编辑 `docker-compose.yml` 中的 `ports` 映射。

---

## TODO：后续待办事项

### 高优先级

- [ ] **配置国内镜像加速**：为 Docker 配置阿里云/中科大镜像源，避免拉取超时
- [ ] **持久化数据备份**：配置 PostgreSQL 和 ClickHouse 数据卷定期备份到外部存储
- [ ] **userId 动态化**：调研是否可通过环境变量或配置文件传入 userId，避免改源码
- [ ] **HTTPS 配置**：生产环境为 Langfuse Web UI 配置 HTTPS（Nginx 反向代理 + Let's Encrypt）

### 中优先级

- [ ] **多项目支持**：验证 `claude-langfuse-monitor` 是否能按不同项目目录区分 project
- [ ] **历史数据迁移**：确认过去 24h 以外的历史 session 是否需要补导入
- [ ] **性能监控**：观察 Langfuse 资源占用（RAM/CPU/Disk），调整 Docker 资源限制
- [ ] **告警配置**：设置磁盘空间告警（ClickHouse 数据增长较快）

### 低优先级 / 优化

- [ ] **自定义仪表盘**：在 Langfuse 中创建团队关注的 KPI 看板（如日均 trace 数、平均工具调用次数）
- [ ] **Trace 标签**：探索为不同项目/任务类型打标签，便于分类检索
- [ ] **升级策略**：关注 Langfuse 版本更新，制定升级流程
- [ ] **文档同步**：将此文档同步到团队 wiki，确保新成员可自助部署
