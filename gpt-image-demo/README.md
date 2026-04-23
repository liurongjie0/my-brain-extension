# ChatGPT 文生图 API Demo

调用 OpenAI 最新文生图模型 (`gpt-image-1` / `gpt-image-1.5` / `gpt-image-2`) 的示例代码。

## 环境准备

```bash
# 1. 安装依赖
pip install -r requirements.txt

# 2. 配置 API Key
cp .env.example .env
# 编辑 .env，填入你的 OpenAI API Key
```

## 模型说明

| 模型 | 状态 | 特点 |
|------|------|------|
| `gpt-image-1` | 已发布 | ChatGPT 原生图像生成能力开放为 API |
| `gpt-image-1.5` | 已发布 | 速度更快，文字渲染更好，价格低 20% |
| `gpt-image-2` | 2026.04 发布 | 质量优先，像素级文字渲染，品牌一致性 |

## 使用示例

### 1. 极简版本 (Images API)

```bash
python generate_image_simple.py
```

使用 OpenAI `images.generate` 端点，兼容 DALL-E 3。如果 `gpt-image-1` 不可用自动回落到 `dall-e-3`。

### 2. Responses API (推荐，支持 gpt-image-2)

```bash
python generate_image.py "一只穿着宇航服的柴犬在月球上" \
  --model gpt-image-1 \
  --size 1024x1024 \
  --quality high \
  --output astronaut_shiba.png
```

Responses API 是 OpenAI 2025 年推出的新接口，原生支持多模态输入输出，是调用 `gpt-image-1/1.5/2` 的推荐方式。

### 3. 图片编辑 (图生图)

```bash
python generate_image_edit.py original.png --prompt "把背景换成星空" --output edited.png
```

支持上传原图 + 遮罩(可选)，对指定区域进行编辑。

## 参数说明

| 参数 | 可选值 | 说明 |
|------|--------|------|
| `size` | `1024x1024`, `1024x1536`, `1536x1024` | 图片尺寸 |
| `quality` | `low`, `medium`, `high`, `auto` | 生成质量 |
| `n` | 1 - 10 | 生成数量 (部分模型限制为 1) |

## 价格参考

- **gpt-image-1**: 文本输入 $5/M tokens, 图像输出 $40/M tokens
- **gpt-image-1.5**: 比 1.0 低约 20%
- **gpt-image-2**: 新定价，请关注 OpenAI 官方

## 注意事项

1. 生成的图片 URL 有效期约 **1 小时**，请及时下载保存
2. 所有请求经过内容安全过滤，违规内容会被拒绝
3. `gpt-image-1` 系列 API 需要申请权限，不是所有账户默认可用
4. 如果提示模型不可用，代码会自动回落到 `dall-e-3`
