# Hiking Bot - 徒步路线规划机器人

企业微信群聊中的徒步路线规划助手，支持自然语言交互、多轮对话追问、路线推荐、日程安排和装备清单生成。

## 核心功能

- **自然语言交互**：@机器人说出需求，如"这周想去杭州周边，10公里左右，中等难度"
- **多轮对话追问**：信息不足时主动询问地点、人数、难度、距离
- **路线推荐**：基于 AI + 上海周边经典路线知识库
- **日程生成**：包含集合时间、交通、徒步时间、返程时间的完整日程表
- **装备清单**：个人必备 + 团队公共物资，按难度自动调整
- **历史记录**：SQLite 持久化保存每次活动

## 快速开始

### 1. 安装依赖

```bash
cd hiking-bot
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 2. 配置

```bash
# 复制配置模板
cp .env.example .env

# 编辑 .env 文件，填写你的配置
vim .env
```

### 3. 启动服务

```bash
python -m app.main
# 或使用 uvicorn
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### 4. 企业微信配置

1. 登录企业微信管理后台
2. 创建自建应用，获取 AgentID 和 Secret
3. 设置接收消息：
   - URL: `http://你的域名/wechat/callback`
   - Token: 与 .env 中 WECHAT_TOKEN 一致
   - EncodingAESKey: 与 .env 中 WECHAT_ENCODING_AES_KEY 一致
4. 将应用添加到目标群聊

## 本地测试

无需配置企微，直接在终端测试对话流程：

```bash
python test_cli.py
```

## 项目结构

```
hiking-bot/
├── app/
│   ├── main.py              # FastAPI 入口
│   ├── config.py            # 配置（从 .env 读取）
│   ├── wechat/              # 企微接入
│   │   ├── callback.py      # 消息回调处理
│   │   └── message.py       # 消息发送
│   ├── conversation/        # 对话管理
│   │   ├── manager.py       # 状态机
│   │   └── states.py        # 状态定义
│   ├── intent/              # 意图识别
│   │   └── extractor.py     # 参数提取
│   ├── generator/           # 内容生成
│   │   ├── route_card.py    # 路线卡片
│   │   ├── itinerary.py     # 日程生成
│   │   └── checklist.py     # 装备清单
│   └── models/              # 数据模型
│       ├── route.py
│       └── history.py
├── data/                    # SQLite 数据库
├── .env.example             # 配置模板
├── test_cli.py              # 本地测试脚本
├── requirements.txt
└── README.md
```

## 使用示例

```
用户: @徒步助手 这周想去杭州周边徒步
助手: 收到！还差一个信息：人数？

用户: 5个人
助手: 收到！还差一个信息：难度偏好？

用户: 中等难度，10公里左右
助手: [路线卡片 + 日程 + 清单]
      满意的话回复"好"，我帮你保存记录；
      不满意回复"换"，我重新推荐。

用户: 好
助手: [完整内容] ✅ 已保存到历史记录！
```

## 配置说明

| 变量 | 说明 | 必填 |
|------|------|------|
| WECHAT_CORP_ID | 企业微信 CorpID | 是 |
| WECHAT_AGENT_ID | 应用 AgentID | 是 |
| WECHAT_SECRET | 应用 Secret | 是 |
| WECHAT_TOKEN | 回调 Token | 是 |
| WECHAT_ENCODING_AES_KEY | 消息加解密密钥 | 是 |
| AI_API_KEY | AI API Key | 否（有 fallback） |
| AI_BASE_URL | AI API 地址 | 否 |
| AI_MODEL | AI 模型 | 否 |

## 后续计划

- [ ] 两步路/小红书爬虫接入
- [ ] 企业微信图文卡片消息
- [ ] 路线轨迹地图展示
- [ ] 天气联动（自动查询目的地天气）
- [ ] 队员报名/人数统计
