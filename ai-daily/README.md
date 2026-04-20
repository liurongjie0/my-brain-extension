# AI Daily — AI 进展日报机器人

每天自动抓取 AI 领域最新进展，用 Claude 做内容总结，推送到企业微信群。

## 内容源

| 来源 | 说明 |
|------|------|
| GitHub Trending | AI/ML 相关热门项目 |
| arXiv | CS.AI / CS.CL / CS.LG 最新论文 |
| Hacker News | AI 相关热帖 |
| TechCrunch | AI 新闻 |
| Product Hunt | AI 新产品 |
| Anthropic Blog | Claude / Anthropic 官方动态 |

## 快速开始

### 1. 安装依赖

```bash
cd ai-daily
pip install -r requirements.txt
```

### 2. 配置

编辑 `config.yaml`：

```yaml
# 填入企业微信机器人 Webhook URL
wechat_webhook: "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=你的KEY"

# 推送时间
push_time: "08:00"

# 内容源开关
sources:
  github_trending: true
  arxiv: true
  hackernews: true
  techcrunch: true
  producthunt: false    # 需配置 access_token
  anthropic: true
```

> Claude API 已配置为 Kimi API（兼容模式），无需额外修改。

### 3. 安装定时任务

```bash
./scripts/install_launchd.sh
```

每天早 8:00 自动推送。日志输出到 `data/stdout.log` 和 `data/stderr.log`。

### 4. 手动运行

```bash
python3 run.py
```

## 项目结构

```
ai-daily/
├── config.yaml              # 配置文件
├── run.py                   # 主入口
├── src/
│   ├── fetchers/            # 内容源抓取器
│   ├── aggregator.py        # 去重 (SQLite)
│   ├── summarizer.py        # Claude API 内容总结
│   ├── formatter.py         # 生成 Markdown 日报
│   └── wechat_bot.py        # 企业微信推送
├── data/                    # SQLite 数据库 + 日志
└── scripts/
    └── install_launchd.sh   # 定时任务安装脚本
```

## 常用命令

```bash
# 查看定时任务状态
launchctl list | grep com.user.ai-daily

# 手动触发一次
launchctl start com.user.ai-daily

# 卸载定时任务
launchctl unload ~/Library/LaunchAgents/com.user.ai-daily.plist
```
