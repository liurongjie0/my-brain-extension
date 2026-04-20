"""LangGraph 实现：Researcher -> Analyst -> Writer（图编排）"""

import json
import os
import sys
from typing import Optional, TypedDict

from dotenv import load_dotenv
from langchain_anthropic import ChatAnthropic
from langgraph.graph import END, StateGraph

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from shared.data_store import search_knowledge_base
from shared.models import AnalysisResult, FinalReport, RawMaterial

load_dotenv()

# 配置 LLM（兼容 Kimi API）
llm = ChatAnthropic(
    model="claude-sonnet-4-6",
    anthropic_api_key=os.getenv("ANTHROPIC_API_KEY") or os.getenv("ANTHROPIC_AUTH_TOKEN", ""),
    anthropic_api_url=os.getenv("ANTHROPIC_BASE_URL"),
    max_tokens=4096,
)


# ========== State 定义（LangGraph 核心概念）==========
class PipelineState(TypedDict):
    """图的状态——所有节点共享的状态容器"""
    topic: str
    raw_sources: list          # 原始检索结果
    material: Optional[RawMaterial]    # Researcher 产出
    analysis: Optional[AnalysisResult] # Analyst 产出
    report: Optional[FinalReport]      # Writer 产出


# ========== Node 函数 ==========
def researcher_node(state: PipelineState) -> dict:
    """节点 1：检索资料并整理"""
    topic = state["topic"]
    sources = search_knowledge_base(topic)

    prompt = (
        f"你是一个研究助理。研究主题：{topic}\n\n"
        f"检索到的资料：\n" + "\n".join(f"- {s}" for s in sources) + "\n\n"
        "请整理成结构化资料。以 JSON 输出："
        '{"topic": "...", "sources": ["..."]}'
    )

    response = llm.invoke([("human", prompt)])

    try:
        text = response.content
        if "```json" in text:
            text = text.split("```json")[1].split("```")[0]
        elif "```" in text:
            text = text.split("```")[1].split("```")[0]
        data = json.loads(text.strip())
        material = RawMaterial(**data)
    except Exception:
        material = RawMaterial(topic=topic, sources=sources)

    material.topic = topic
    if not material.sources:
        material.sources = sources

    print(f"[Researcher Node] 检索到 {len(material.sources)} 条资料")
    return {"raw_sources": sources, "material": material}


def analyst_node(state: PipelineState) -> dict:
    """节点 2：分析资料"""
    material = state["material"]
    if not material:
        return {"analysis": None}

    prompt = (
        f"你是一个分析师。研究主题：{material.topic}\n\n"
        f"原始资料：\n" + "\n".join(f"- {s}" for s in material.sources) + "\n\n"
        "请提取关键观点、趋势和空白。以 JSON 输出："
        '{"topic": "...", "key_points": ["..."], "trends": ["..."], "gaps": ["..."]}'
    )

    response = llm.invoke([("human", prompt)])

    try:
        text = response.content
        if "```json" in text:
            text = text.split("```json")[1].split("```")[0]
        elif "```" in text:
            text = text.split("```")[1].split("```")[0]
        data = json.loads(text.strip())
        analysis = AnalysisResult(**data)
    except Exception:
        analysis = AnalysisResult(
            topic=material.topic,
            key_points=[response.content[:300]],
            trends=[],
            gaps=[],
        )

    analysis.topic = material.topic
    print(f"[Analyst Node] 提取了 {len(analysis.key_points)} 个观点")
    return {"analysis": analysis}


def writer_node(state: PipelineState) -> dict:
    """节点 3：生成报告"""
    analysis = state["analysis"]
    if not analysis:
        return {"report": None}

    prompt = (
        f"你是一个技术写作专家。研究主题：{analysis.topic}\n\n"
        f"关键观点：\n" + "\n".join(f"- {p}" for p in analysis.key_points) + "\n\n"
        f"趋势：\n" + "\n".join(f"- {t}" for t in analysis.trends) + "\n\n"
        f"空白：\n" + "\n".join(f"- {g}" for g in analysis.gaps) + "\n\n"
        "请生成 Markdown 报告。以 JSON 输出："
        '{"topic": "...", "summary": "...", "sections": [{"title": "...", "content": "..."}], "markdown": "..."}'
    )

    response = llm.invoke([("human", prompt)])

    try:
        text = response.content
        if "```json" in text:
            text = text.split("```json")[1].split("```")[0]
        elif "```" in text:
            text = text.split("```")[1].split("```")[0]
        data = json.loads(text.strip())
        report = FinalReport(**data)
    except Exception:
        report = FinalReport(
            topic=analysis.topic,
            summary=response.content[:300],
            sections=[],
            markdown=response.content,
        )

    report.topic = analysis.topic
    print(f"[Writer Node] 报告生成完成")
    return {"report": report}


def should_continue(state: PipelineState) -> str:
    """条件边：检查是否可以继续"""
    if state.get("report"):
        return "end"
    if state.get("analysis"):
        return "writer"
    if state.get("material"):
        return "analyst"
    return "researcher"


# ========== 构建图 ==========
workflow = StateGraph(PipelineState)

# 添加节点
workflow.add_node("researcher", researcher_node)
workflow.add_node("analyst", analyst_node)
workflow.add_node("writer", writer_node)

# 添加边
workflow.set_entry_point("researcher")
workflow.add_edge("researcher", "analyst")
workflow.add_edge("analyst", "writer")
workflow.add_edge("writer", END)

# 编译图
graph = workflow.compile()


def run(topic: str) -> FinalReport:
    """运行完整流水线"""
    print(f"\n{'='*50}")
    print(f"LangGraph Pipeline")
    print(f"主题：{topic}")
    print(f"{'='*50}\n")

    # 初始状态
    initial_state = PipelineState(
        topic=topic,
        raw_sources=[],
        material=None,
        analysis=None,
        report=None,
    )

    # 执行图
    result = graph.invoke(initial_state)

    return result["report"]


if __name__ == "__main__":
    topic = sys.argv[1] if len(sys.argv) > 1 else "多智能体框架"
    report = run(topic)

    if report:
        print(f"\n{'='*50}")
        print("最终报告：")
        print(f"{'='*50}\n")
        print(report.markdown)
    else:
        print("报告生成失败")
