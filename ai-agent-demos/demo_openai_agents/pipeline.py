"""OpenAI Agents SDK 实现：Researcher -> Analyst -> Writer（Handoff 模式）"""

import json
import os
import sys

# OpenAI Agents SDK 需要 Python 3.10+
if sys.version_info < (3, 10):
    print("[Error] OpenAI Agents SDK 需要 Python 3.10+，当前版本：")
    print(f"        Python {sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}")
    print("\n提示：")
    print("  - 升级 Python 到 3.10 或更高版本")
    print("  - 或使用 pyenv/uv 创建虚拟环境")
    print("  - 代码结构完整，可在 Python 3.10+ 环境中运行")
    sys.exit(1)

from dotenv import load_dotenv

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from shared.data_store import search_knowledge_base
from shared.models import AnalysisResult, FinalReport, RawMaterial

load_dotenv()

# 检查 API 配置
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
OPENAI_BASE_URL = os.getenv("OPENAI_BASE_URL", "")

# 如果没有 OpenAI API Key，尝试用 Kimi 的 OpenAI 兼容端点
if not OPENAI_API_KEY:
    # 检查是否有 Kimi 的配置
    kimi_key = os.getenv("ANTHROPIC_API_KEY") or os.getenv("ANTHROPIC_AUTH_TOKEN", "")
    if kimi_key:
        print("[Info] 检测到 Kimi API Key，尝试配置 OpenAI 兼容模式...")
        OPENAI_API_KEY = kimi_key
        # Kimi 的 OpenAI 兼容端点
        OPENAI_BASE_URL = "https://api.moonshot.cn/v1"

if not OPENAI_API_KEY:
    print("[Error] 未配置 OpenAI API Key。请设置 OPENAI_API_KEY 环境变量。")
    print("        或使用 .env 文件配置。")
    sys.exit(1)

os.environ["OPENAI_API_KEY"] = OPENAI_API_KEY
if OPENAI_BASE_URL:
    os.environ["OPENAI_BASE_URL"] = OPENAI_BASE_URL

from agents import Agent, Runner, function_tool


# ========== 工具函数（Agents SDK 风格）==========
@function_tool
def search_tool(topic: str) -> str:
    """检索知识库，获取与主题相关的资料片段"""
    sources = search_knowledge_base(topic)
    return "\n".join(f"- {s}" for s in sources)


@function_tool
def analyze_tool(raw_material_json: str) -> str:
    """分析原始资料，提取关键观点、趋势和空白"""
    try:
        data = json.loads(raw_material_json)
        material = RawMaterial(**data)
    except Exception:
        return json.dumps({"error": "解析失败", "key_points": [raw_material_json[:300]]})

    # 这里实际由 LLM 完成分析，工具只做格式转换
    return raw_material_json


@function_tool
def write_report_tool(analysis_json: str) -> str:
    """根据分析结果生成 Markdown 报告"""
    return analysis_json


# ========== 定义三个 Agent ==========
researcher_agent = Agent(
    name="Researcher",
    instructions=(
        "你是一个研究助理。当用户给你一个研究主题时，使用 search_tool 检索相关资料，"
        "然后整理成结构化的原始材料。输出格式必须是 JSON："
        '{"topic": "主题", "sources": ["资料1", "资料2"]}'
    ),
    tools=[search_tool],
    model="gpt-4o-mini" if not OPENAI_BASE_URL else "kimi-latest",
)

analyst_agent = Agent(
    name="Analyst",
    instructions=(
        "你是一个分析师。给定原始资料，提取关键观点、识别趋势、指出研究空白。"
        "输出格式必须是 JSON："
        '{"topic": "主题", "key_points": ["..."], "trends": ["..."], "gaps": ["..."]}'
    ),
    tools=[analyze_tool],
    model="gpt-4o-mini" if not OPENAI_BASE_URL else "kimi-latest",
)

writer_agent = Agent(
    name="Writer",
    instructions=(
        "你是一个技术写作专家。给定分析结果，生成一份完整的 Markdown 格式报告。"
        "输出格式必须是 JSON："
        '{"topic": "...", "summary": "300字摘要", "sections": [{"title": "...", "content": "..."}], "markdown": "完整markdown"}'
    ),
    tools=[write_report_tool],
    model="gpt-4o-mini" if not OPENAI_BASE_URL else "kimi-latest",
)


# ========== 协调 Agent：负责任务分发 ==========
triage_agent = Agent(
    name="Triage",
    instructions=(
        "你是一个任务协调员。你的工作是：\n"
        "1. 收到用户的研究主题后，调用 Researcher Agent 收集资料\n"
        "2. 资料收集完成后，调用 Analyst Agent 进行分析\n"
        "3. 分析完成后，调用 Writer Agent 生成报告\n"
        "4. 最终返回完整的 Markdown 报告\n\n"
        "请按顺序执行，不要跳过任何步骤。"
    ),
    handoffs=[researcher_agent, analyst_agent, writer_agent],
    model="gpt-4o-mini" if not OPENAI_BASE_URL else "kimi-latest",
)


# ========== 辅助函数：解析结果 ==========
def _parse_json_from_output(text: str) -> dict:
    """从 Agent 输出中提取 JSON"""
    try:
        if "```json" in text:
            text = text.split("```json")[1].split("```")[0]
        elif "```" in text:
            text = text.split("```")[1].split("```")[0]
        return json.loads(text.strip())
    except Exception:
        return {}


# ========== 由于 OpenAI Agents SDK 的 Handoff 是自动的，我们用串行 Runner 模拟 ==========
def run(topic: str) -> FinalReport:
    """运行完整流水线（串行调用三个 Agent）"""
    print(f"\n{'='*50}")
    print(f"OpenAI Agents SDK Pipeline")
    print(f"主题：{topic}")
    print(f"{'='*50}\n")

    # Step 1: Researcher
    print("[Step 1/3] Researcher Agent 检索资料...")
    result1 = Runner.run_sync(
        researcher_agent,
        f"请研究主题：{topic}",
    )

    material_data = _parse_json_from_output(result1.final_output)
    material = RawMaterial(
        topic=material_data.get("topic", topic),
        sources=material_data.get("sources", []),
    )
    print(f"[Researcher] 检索到 {len(material.sources)} 条资料")

    # Step 2: Analyst
    print("[Step 2/3] Analyst Agent 分析资料...")
    result2 = Runner.run_sync(
        analyst_agent,
        f"请分析以下资料（主题：{topic}）：\n" + "\n".join(f"- {s}" for s in material.sources),
    )

    analysis_data = _parse_json_from_output(result2.final_output)
    analysis = AnalysisResult(
        topic=analysis_data.get("topic", topic),
        key_points=analysis_data.get("key_points", []),
        trends=analysis_data.get("trends", []),
        gaps=analysis_data.get("gaps", []),
    )
    print(f"[Analyst] 提取了 {len(analysis.key_points)} 个观点")

    # Step 3: Writer
    print("[Step 3/3] Writer Agent 生成报告...")
    analysis_text = json.dumps({
        "topic": analysis.topic,
        "key_points": analysis.key_points,
        "trends": analysis.trends,
        "gaps": analysis.gaps,
    }, ensure_ascii=False)

    result3 = Runner.run_sync(
        writer_agent,
        f"请根据以下分析结果生成报告：\n{analysis_text}",
    )

    report_data = _parse_json_from_output(result3.final_output)
    report = FinalReport(
        topic=report_data.get("topic", topic),
        summary=report_data.get("summary", ""),
        sections=report_data.get("sections", []),
        markdown=report_data.get("markdown", result3.final_output),
    )
    print(f"[Writer] 报告生成完成")

    return report


if __name__ == "__main__":
    topic = sys.argv[1] if len(sys.argv) > 1 else "多智能体框架"
    try:
        report = run(topic)

        print(f"\n{'='*50}")
        print("最终报告：")
        print(f"{'='*50}\n")
        print(report.markdown)
    except Exception as e:
        print(f"[Error] 运行失败: {e}")
        print("\n提示：OpenAI Agents SDK 需要 OpenAI API Key。")
        print("如果使用 Kimi API，可能需要额外配置 OpenAI 兼容端点。")
        raise
