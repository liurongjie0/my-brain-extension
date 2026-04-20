from anthropic import Anthropic

from .fetchers.base import NewsItem


class Summarizer:
    def __init__(self, api_key: str, base_url: str, model: str, max_length: int = 60):
        self.client = Anthropic(api_key=api_key, base_url=base_url)
        self.model = model
        self.max_length = max_length

    def summarize(self, items: list[NewsItem]) -> list[NewsItem]:
        summarized = []
        for item in items:
            if item.description:
                try:
                    prompt = (
                        f"请用一句话（不超过{self.max_length}个字）总结以下内容：\n"
                        f"标题：{item.title}\n"
                        f"描述：{item.description[:500]}"
                    )
                    resp = self.client.messages.create(
                        model=self.model,
                        max_tokens=100,
                        messages=[{"role": "user", "content": prompt}],
                    )
                    summary = resp.content[0].text.strip()
                    if len(summary) > self.max_length + 20:
                        summary = summary[: self.max_length] + "..."
                    item.description = summary
                except Exception:
                    pass
            summarized.append(item)
        return summarized
