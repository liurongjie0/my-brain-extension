#!/usr/bin/env python3
"""
ChatGPT 图生图 / 图片编辑 Demo
支持第三方代理 (如 gptsapi.net)
"""

import argparse
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

    parser = argparse.ArgumentParser(description="ChatGPT 图片编辑")
    parser.add_argument("image", help="原始图片路径")
    parser.add_argument("mask", nargs="?", help="遮罩图片路径")
    parser.add_argument("--prompt", required=True, help="编辑指令")
    parser.add_argument("--output", default="output_edited.png", help="输出路径")
    args = parser.parse_args()

    client = OpenAI(api_key=api_key, base_url=base_url)

    print(f"代理: {base_url or '官方'}")
    print(f"原始图片: {args.image}")
    print(f"编辑指令: {args.prompt}")

    try:
        with open(args.image, "rb") as img:
            kwargs = {
                "image": img,
                "prompt": args.prompt,
                "model": "dall-e-2",
                "n": 1,
                "size": "1024x1024",
            }
            if args.mask:
                kwargs["mask"] = open(args.mask, "rb")
            response = client.images.edit(**kwargs)
    except Exception as e:
        print(f"编辑失败: {e}")
        sys.exit(1)

    image_url = response.data[0].url
    print(f"图片 URL: {image_url}")

    image_data = requests.get(image_url).content
    output = Path(args.output)
    output.write_bytes(image_data)
    print(f"已保存到: {output.resolve()}")


if __name__ == "__main__":
    main()
