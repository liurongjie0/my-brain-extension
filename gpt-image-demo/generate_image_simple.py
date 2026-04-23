#!/usr/bin/env python3
"""
ChatGPT 文生图 - 极简版本
支持第三方代理 (如 gptsapi.net)

可用模型: gpt-image-1.5, gpt-image-2-plus, grok-imagine-image 等
"""

import base64
import os
import sys
from pathlib import Path

import requests
from dotenv import load_dotenv
from openai import OpenAI

def main():
    load_dotenv()
    api_key = os.getenv("OPENAI_API_KEY")
    base_url = os.getenv("OPENAI_BASE_URL")
    if not api_key:
        print("请设置 OPENAI_API_KEY 环境变量")
        sys.exit(1)

    prompt = "一只可爱的柴犬在樱花树下，吉卜力工作室风格，温暖的阳光"

    client = OpenAI(api_key=api_key, base_url=base_url)

    print(f"代理: {base_url or '官方'}")
    print(f"提示词: {prompt}")
    print("正在生成图片...")

    try:
        response = client.images.generate(
            model="gpt-image-1.5",
            prompt=prompt,
            size="1024x1024",
            quality="high",
            n=1,
        )
    except Exception as e:
        print(f"gpt-image-1.5 不可用 ({e}), 尝试 gpt-image-2-plus...")
        response = client.images.generate(
            model="gpt-image-2-plus",
            prompt=prompt,
            size="1024x1024",
            quality="high",
            n=1,
        )

    # 处理返回: 可能是 URL 或 base64
    image_data = None
    if response.data[0].url:
        image_url = response.data[0].url
        print(f"图片 URL: {image_url}")
        image_data = requests.get(image_url).content
    elif response.data[0].b64_json:
        print("图片以 base64 格式返回")
        image_data = base64.b64decode(response.data[0].b64_json)
    else:
        print("错误: 未获取到图片数据")
        sys.exit(1)

    output = Path("output_simple.png")
    output.write_bytes(image_data)
    print(f"已保存到: {output.resolve()} ({len(image_data)} bytes)")


if __name__ == "__main__":
    main()
