"""LangChain LCEL 实现：Researcher -> Analyst -> Writer"""

import json
import os
import sys

from dotenv import load_dotenv
from langchain.prompts import ChatPromptTemplate
from langchain.schema.runnable import RunnableLambda, RunnableParallel, RunnableSequence
from langchain_anthropic import ChatAnthropic

# 加载环境变量
load_dotenv()

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from shared.data_store import search_knowledge_base
from shared.models import AnalysisResult, FinalReport, RawMaterial

# 配置 LLM（兼容 Kimi API）
llm = ChatAnthropic(
    model="claude-sonnet-4-6",
    anthropic_api_key=os.getenv("ANTHROPIC_API_KEY") or os.getenv("ANTHROPIC_AUTH_TOKEN", ""),
    anthropic_api_url=os.getenv("ANTHROPIC_BASE_URL"),
    max_tokens=4096,
)


# ========== Step 1: Researcher ==========
researcher_prompt = ChatPromptTemplate.from_messages([
    ("system", "你是一个研究助理。给定一个研究主题，你需要从知识库中检索相关信息，整理成结构化资料。"),
    ("human", "研究主题：{topic}\n\n检索到的资料片段：\n{sources}\n\n请将这些资料整理成结构化的原始材料，以 JSON 格式输出：\n{{\"topic\": \"...\", \"sources\": [\"...\", \"...\"]}}"),
])


def _parse_raw_material(text: str) -> RawMaterial:
    """从 LLM 输出解析 RawMaterial"""
    try:
        # 尝试提取 JSON
        if "```json" in text:
            text = text.split("```json")[1].split("```")[0]
        elif "```" in text:
            text = text.split("```")[1].split("```")[0]
        data = json.loads(text.strip())
        return RawMaterial(**data)
    except Exception:
        # 回退：构造一个基本的 RawMaterial
        return RawMaterial(topic="", sources=[text[:500]])


def researcher_step(inputs: dict) -> dict:
    """Researcher Agent：检索并整理资料"""
    topic = inputs["topic"]
    sources = search_knowledge_base(topic)

    chain = researcher_prompt | llm
    result = chain.invoke({"topic": topic, "sources": "\n".join(f"- {s}" for s in sources)})

    material = _parse_raw_material(result.content)
    material.topic = topic
    if not material.sources:
        material.sources = sources

    print(f"[Researcher] 检索到 {len(material.sources)} 条资料")
    return {"topic": topic, "material": material}


# ========== Step 2: Analyst ==========
analyst_prompt = ChatPromptTemplate.from_messages([
    ("system", "你是一个分析师。给定原始资料，你需要提取关键观点、识别趋势、指出空白。"),
    ("human", "研究主题：{topic}\n\n原始资料：\n{sources}\n\n请分析并输出 JSON 格式：\n{{\"topic\": \"...\", \"key_points\": [\"...\"], \"trends\": [\"...\"], \"gaps\": [\"...\"]}}"),
])


def _parse_analysis(text: str) -> AnalysisResult:
    try:
        if "```json" in text:
            text = text.split("```json")[1].split("```")[0]
        elif "```" in text:
            text = text.split("```")[1].split("```")[0]
        data = json.loads(text.strip())
        return AnalysisResult(**data)
    except Exception:
        return AnalysisResult(topic="", key_points=[text[:300]], trends=[], gaps=[])


def analyst_step(inputs: dict) -> dict:
    """Analyst Agent：分析资料"""
    material = inputs["material"]

    chain = analyst_prompt | llm
    result = chain.invoke({
        "topic": material.topic,
        "sources": "\n".join(f"- {s}" for s in material.sources),
    })

    analysis = _parse_analysis(result.content)
    analysis.topic = material.topic

    print(f"[Analyst] 提取了 {len(analysis.key_points)} 个关键观点，{len(analysis.trends)} 个趋势")
    return {"topic": inputs["topic"], "material": material, "analysis": analysis}


# ========== Step 3: Writer ==========
writer_prompt = ChatPromptTemplate.from_messages([
    ("system", "你是一个技术写作专家。给定分析结果，生成一份结构化的 Markdown 报告。"),
    ("human", "研究主题：{topic}\n\n关键观点：\n{key_points}\n\n趋势：\n{trends}\n\n空白：\n{gaps}\n\n请生成一份完整的 Markdown 格式报告，包含摘要和分节内容。以 JSON 格式输出：\n{{\"topic\": \"...\", \"summary\": \"...\", \"sections\": [{{\"title\": \"...\", \"content\": \"...\"}}], \"markdown\": \"...\"}}"),
])


def _parse_report(text: str) -> FinalReport:
    try:
        if "```json" in text:
            text = text.split("```json")[1].split("```")[0]
        elif "```" in text:
            text = text.split("```")[1].split("```")[0]
        data = json.loads(text.strip())
        return FinalReport(**data)
    except Exception:
        return FinalReport(
            topic="",
            summary=text[:300],
            sections=[],
            markdown=text,
        )


def writer_step(inputs: dict) -> FinalReport:
    """Writer Agent：生成报告"""
    analysis = inputs["analysis"]

    chain = writer_prompt | llm
    result = chain.invoke({
        "topic": analysis.topic,
        "key_points": "\n".join(f"- {p}" for p in analysis.key_points),
        "trends": "\n".join(f"- {t}" for t in analysis.trends),
        "gaps": "\n".join(f"- {g}" for g in analysis.gaps),
    })

    report = _parse_report(result.content)
    report.topic = analysis.topic

    print(f"[Writer] 报告生成完成，{len(report.summary)} 字摘要")
    return report


# ========== 组装 LCEL Chain ==========
# LangChain 的核心编排方式：用 | 操作符串联 Runnable
pipeline = (
    RunnableLambda(researcher_step)
    | RunnableLambda(analyst_step)
    | RunnableLambda(writer_step)
)


def run(topic: str) -> FinalReport:
    """运行完整流水线"""
    print(f"\n{'='*50}")
    print(f"LangChain LCEL Pipeline")
    print(f"主题：{topic}")
    print(f"{'='*50}\n")
    return pipeline.invoke({"topic": topic})


if __name__ == "__main__":
    topic = sys.argv[1] if len(sys.argv) > 1 else "多智能体框架"
    report = run(topic)

    print(f"\n{'='*50}")
    print("最终报告：")
    print(f"{'='*50}\n")
    print(report.markdown)
