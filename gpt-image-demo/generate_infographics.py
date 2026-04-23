#!/usr/bin/env python3
"""
批量生成 Notion 风格信息图
AI Coding 发展历史 & SDD 驱动编程实战
模型: gpt-image-2-plus
提示词: 中文
"""

import base64
import os
from pathlib import Path

from dotenv import load_dotenv
from openai import OpenAI

load_dotenv()

client = OpenAI(
    api_key=os.getenv("OPENAI_API_KEY"),
    base_url=os.getenv("OPENAI_BASE_URL"),
)

OUTPUT_DIR = Path("/Users/liurongjie/my-brain-extension/gpt-image-demo/infographics")
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

STYLE_PREFIX = """Notion官方插画风格松弛感线稿信息图。

线条特征：
- 纯黄色为主，搭配其他适合的颜色
- 线条粗细不均匀，像马克笔随手画的质感
- 笔触松弛、略带抖动，不追求工整
- 所有图标、人物、图表都保持手绘涂鸦感
- 少部份地方填充黑色让整体压实

人物画法（如需要）：
- 简笔画风格：女孩，短发，圆润的头、点或线表示五官
- 肢体夸张、动作松弛自然
- 可以是完整人物或只露出局部（手、上半身）"""

STYLE_SUFFIX = """禁止事项：
- 不要彩色渐变或复杂配色
- 不要粗黑边框或生硬分隔线
- 不要3D效果、阴影、立体感
- 不要密集文字堆砌，文字内容简化
- 保持大量留白和呼吸感
- 装饰元素要克制，不要太多

输出规范：
- 图片比例16:9
- 中文标注
- 直接生成，不需要解释"""

IMAGES = [
    {
        "filename": "v2-infographic-01-cover.png",
        "content": """一张科技分享会的标题封面。画面中央是大号手绘字体"AI Coding Evolution"，副标题"从Copilot到SDD驱动开发"。一位短发简笔画女孩坐在书桌前使用笔记本电脑，周围漂浮着手绘涂鸦图标：代码括号、机器人头像、灯泡和对话气泡。笔记本屏幕显示流动的代码行。画面上方有一条时间弧线连接三个时代的简单图标：键盘（手工编码）、机械臂（AI辅助）、多个连接节点（智能体协作）。构图干净，中央元素周围留有大量留白。""",
    },
    {
        "filename": "v2-infographic-02-history.png",
        "content": """一条水平时间轴，展示AI编程发展的三个时代。时代一（左侧）："代码补全时代"——一位程序员独自敲键盘，身旁漂浮着一个小幽灵般的AI，正在提示代码片段。时代二（中间）："AI对话时代"——程序员正与一个带有机器人脸的对话气泡交谈，显示来回的对话箭头。时代三（右侧）："AI智能体时代"——程序员双手抱胸站在一旁，多个小型AI智能体围绕屏幕自主编写代码。三个时代由流动的虚线和箭头标记连接。每个时代下方有简单的年份标签。手绘风格，黄色马克笔点缀。""",
    },
    {
        "filename": "v2-infographic-03-tools.png",
        "content": """三列对比布局，展示AI编程工具的演变。左列："Copilot时代"——一个代码建议框漂浮在IDE窗口旁边，代表行内补全。中列："ChatGPT时代"——一位程序员在大型对话界面中输入问题，收到代码块回复。右列："Claude Code时代"——一个终端窗口显示自主智能体执行，任务列表、文件编辑和测试运行同时进行。每列顶部有一个简单图标：幽灵、对话气泡、机器人。由向上的箭头连接，显示演进过程。关键元素用黄色马克笔高亮。""",
    },
    {
        "filename": "v2-infographic-04-paradigm.png",
        "content": """三个并排场景，展示编程范式的转变。场景一"手工编码"：一位孤独的程序员弯腰趴在键盘上，满头大汗，周围是咖啡杯和便利贴，一切靠手动。场景二"结对编程"：两个人坐在一起——一个人类，一个机器人——在同一个屏幕前协作，指指点点，讨论交流。场景三"编排调度"：人类自信地拿着指挥棒指挥，多个AI智能体（小型机器人形象）围绕在旁，在多个屏幕上执行任务。人类形象在三个场景中变得越来越放松、越来越挺直。简单的视觉叙事，元素极简。""",
    },
    {
        "filename": "v2-infographic-05-sdd-intro.png",
        "content": """一张解释SDD（子智能体驱动开发）的插画。画面中央，一个人类形象（放松地抱胸站立）站在指挥台前，大屏幕显示项目计划。周围有三个小型AI智能体形象，分别戴着标有"头脑风暴"、"写代码"和"代码评审"的帽子。每个智能体都在自己的小办公桌前用笔记本电脑工作。箭头展示工作流程：头脑风暴智能体把想法传给写代码智能体，写代码智能体把代码传给评审智能体，评审智能体把反馈传回人类。人类形象微笑着喝咖啡。顶部文字标签"SDD：让AI智能体做重活"。简单的协作工作空间氛围。""",
    },
    {
        "filename": "v2-infographic-06-sdd-flow.png",
        "content": """一个圆形四步流程图，展示SDD工作流程。中心是一个灯泡涂鸦，代表项目创意。四个步骤按顺时针排列：步骤一"头脑风暴"——一个带着思考气泡的形象，正在探索想法。步骤二"规划"——一个形象在白板上写字，有便利贴和箭头。步骤三"执行"——一个机器人形象用多只手快速敲代码。步骤四"回顾"——一个形象拿着放大镜审查代码。箭头连接步骤形成循环。每个步骤有简单图标和标签。圆圈用松弛的、略带波浪的黄色线条绘制。背景是干净的白色，带有 subtle 的网格点，暗示笔记本页面。""",
    },
    {
        "filename": "v2-infographic-07-summary.png",
        "content": """一张行动号召的结尾幻灯片。顶部是大号粗体手绘文字"未来：AI原生开发者"。下方，两条对比路径以分叉道路的形式呈现。左侧路径"使用AI"：一个人物悠闲地走着，机器人在周围做所有工作。右侧路径"驾驭AI"：同一个人物现在像指挥家一样指挥一支专业化的AI智能体团队，周围漂浮着架构图和战略计划。一个箭头从左指向右，标注"你的旅程"。底部是一个简单的清单："学习工具 → 用智能体构建 → 分享成果"。整体基调鼓舞人心、面向未来。人物是与前面图片相同的短发简笔画女孩，现在看起来自信而充满力量。""",
    },
]


def generate_image(prompt: str, filename: str):
    """生成单张图片"""
    print(f"\n生成: {filename}")
    print(f"提示词长度: {len(prompt)} chars")

    try:
        response = client.images.generate(
            model="gpt-image-2-plus",
            prompt=prompt,
            size="1024x1024",
            quality="high",
            n=1,
        )

        if response.data[0].b64_json:
            image_data = base64.b64decode(response.data[0].b64_json)
        elif response.data[0].url:
            import requests
            image_data = requests.get(response.data[0].url).content
        else:
            print(f"  错误: 未获取到图片数据")
            return False

        output_path = OUTPUT_DIR / filename
        output_path.write_bytes(image_data)
        print(f"  已保存: {output_path} ({len(image_data)} bytes)")
        return True

    except Exception as e:
        print(f"  失败: {e}")
        return False


def main():
    print("=" * 60)
    print("Notion 风格信息图批量生成")
    print("模型: gpt-image-2-plus")
    print("提示词: 中文")
    print("主题: AI Coding 发展历史 & SDD 驱动编程实战")
    print("=" * 60)

    success_count = 0
    for i, img in enumerate(IMAGES, 1):
        full_prompt = f"{STYLE_PREFIX}\n\n{img['content']}\n\n{STYLE_SUFFIX}"
        print(f"\n[{i}/7] ", end="")
        if generate_image(full_prompt, img["filename"]):
            success_count += 1

    print(f"\n{'=' * 60}")
    print(f"完成: {success_count}/{len(IMAGES)} 张图片生成成功")
    print(f"输出目录: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
