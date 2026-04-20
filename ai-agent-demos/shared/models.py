from pydantic import BaseModel, Field


class RawMaterial(BaseModel):
    """Researcher 阶段产出的原始资料"""
    topic: str = Field(description="研究主题")
    sources: list[str] = Field(description="检索到的相关文本片段")


class AnalysisResult(BaseModel):
    """Analyst 阶段产出的分析结果"""
    topic: str = Field(description="研究主题")
    key_points: list[str] = Field(description="提取的关键观点")
    trends: list[str] = Field(description="识别出的趋势")
    gaps: list[str] = Field(description="存在的空白/待解决问题")


class FinalReport(BaseModel):
    """Writer 阶段产出的最终报告"""
    topic: str = Field(description="研究主题")
    summary: str = Field(description="300字以内的摘要")
    sections: list[dict] = Field(description="报告各章节")
    markdown: str = Field(description="完整 Markdown 格式报告")
