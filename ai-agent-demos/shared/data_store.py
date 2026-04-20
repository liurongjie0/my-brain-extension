"""模拟知识库检索——消除数据源差异，确保对比的是框架本身。"""

# 预设的知识库片段，按主题索引
_KNOWLEDGE_BASE: dict[str, list[str]] = {
    "agent": [
        "OpenAI Agents SDK (2025.3) 是一个轻量级多智能体框架，前身是 Swarm。核心概念：Agent（LLM+指令+工具）、Handoff（转接）、Guardrails（护栏）。",
        "LangGraph (2024) 是 LangChain 生态的图编排框架。核心概念：State（状态）、Node（节点）、Edge（边）。支持循环、条件分支、断点续传。",
        "LangChain (2022) 是 LLM 应用开发框架。核心概念：Chains（链）、Retrievers（检索器）、Memory（记忆）。提供 200+ 集成。",
        "CrewAI 是角色驱动的多 Agent 框架，适合业务流程自动化，学习曲线最低。",
        "AutoGen (Microsoft) 专注于代码执行 Agent 和研究场景，支持多 Agent 对话。",
    ],
    "llm": [
        "GPT-4o (2024.5) 是 OpenAI 的多模态旗舰模型，支持文本、图像、音频输入输出。",
        "Claude 4 (2025) 包含 Opus 4.7 和 Sonnet 4.6，上下文窗口达 1M tokens，代码能力突出。",
        "Gemini 2.5 Pro (2025) Google 的多模态模型，支持原生视频理解和超长上下文。",
        "DeepSeek-V3 (2024.12) 以极低成本实现 GPT-4 级别性能，MoE 架构，671B 参数。",
        "Llama 3.3 (2024.12) Meta 开源模型，405B 参数，性能接近 GPT-4o。",
    ],
    "rag": [
        "RAG（检索增强生成）是 2023 年最流行的 LLM 应用架构，核心是将外部知识注入提示。",
        "向量数据库（Pinecone、Weaviate、Milvus）是 RAG 的基础设施，用于语义检索。",
        "GraphRAG (Microsoft 2024) 将知识图谱与 RAG 结合，提升复杂查询的理解能力。",
        "长上下文模型（1M+ tokens）正在挑战 RAG 的必要性，但检索在精确性和成本上仍有优势。",
        "Late Chunking (Jina AI 2024) 是一种新的嵌入策略，在分块前先编码，保留跨块语义。",
    ],
}


def search_knowledge_base(topic: str, max_results: int = 5) -> list[str]:
    """根据主题检索相关知识片段。

    Args:
        topic: 用户输入的研究主题
        max_results: 最多返回多少条

    Returns:
        匹配的知识片段列表
    """
    topic_lower = topic.lower()
    results = []

    # 尝试关键词匹配
    for key, docs in _KNOWLEDGE_BASE.items():
        if key in topic_lower:
            results.extend(docs)

    # 如果没匹配到，返回所有主题的混合
    if not results:
        for docs in _KNOWLEDGE_BASE.values():
            results.extend(docs[:2])

    return results[:max_results]
