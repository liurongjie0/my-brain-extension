#!/usr/bin/env python3
"""
ChatGPT 文生图 API Demo
支持第三方代理 (如 gptsapi.net)

用法:
    python generate_image.py "一只穿着宇航服的猫咪在月球上"
    python generate_image.py "Ghibli style, a cat reading book" --size 1024x1536 --output cat.png
"""

import argparse
import base64
import os
import sys
from pathlib import Path

import requests
from dotenv import load_dotenv
from openai import OpenAI

def load_config():
    load_dotenv()
    api_key = os.getenv("OPENAI_API_KEY")
    base_url = os.getenv("OPENAI_BASE_URL")
    if not api_key:
        print("错误: 请设置 OPENAI_API_KEY 环境变量，或创建 .env 文件")
        sys.exit(1)
    return api_key, base_url


def save_image(content: bytes, path: str):
    output = Path(path)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(content)
    print(f"图片已保存: {output.resolve()} ({len(content)} bytes)")


def get_image_data(response):
    """从响应中提取图片数据，支持 URL 和 base64"""
    if response.data[0].url:
        return requests.get(response.data[0].url).content
    elif response.data[0].b64_json:
        return base64.b64decode(response.data[0].b64_json)
    raise RuntimeError("未获取到图片数据")


def main():
    parser = argparse.ArgumentParser(description="ChatGPT 文生图 Demo")
    parser.add_argument("prompt", help="图片描述文本")
    parser.add_argument("--model", default="gpt-image-1.5", help="模型名称")
    parser.add_argument("--size", default="1024x1024", help="图片尺寸")
    parser.add_argument("--quality", default="high", help="图片质量")
    parser.add_argument("--output", default="output.png", help="输出文件路径")
    args = parser.parse_args()

    api_key, base_url = load_config()

    print(f"代理: {base_url or '官方'}")
    print(f"模型: {args.model}")
    print(f"提示词: {args.prompt}")
    print(f"尺寸: {args.size}")
    print(f"质量: {args.quality}")
    print("生成中，请稍候...\n")

    try:
        client = OpenAI(api_key=api_key, base_url=base_url)
        response = client.images.generate(
            model=args.model,
            prompt=args.prompt,
            size=args.size,
            quality=args.quality,
            n=1,
        )
        image_data = get_image_data(response)
        save_image(image_data, args.output)

    except Exception as e:
        print(f"生成失败: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
