import datetime

from .fetchers.base import NewsItem


class Formatter:
    SOURCE_EMOJI = {
        "github_trending": "📦",
        "arxiv": "📄",
        "hackernews": "💬",
        "techcrunch": "📰",
        "producthunt": "🚀",
        "anthropic": "🤖",
    }

    SOURCE_NAME = {
        "github_trending": "GitHub 热门",
        "arxiv": "今日论文",
        "hackernews": "HN 热帖",
        "techcrunch": "TechCrunch",
        "producthunt": "Product Hunt",
        "anthropic": "Anthropic 动态",
    }

    def format_daily(self, grouped: dict[str, list[NewsItem]]) -> str:
        today = datetime.datetime.now().strftime("%Y/%m/%d")
        lines = [f"🤖 AI 日报 — {today}", ""]

        for source, items in grouped.items():
            if not items:
                continue
            emoji = self.SOURCE_EMOJI.get(source, "•")
            name = self.SOURCE_NAME.get(source, source)
            lines.append(f"{emoji} {name}")
            for item in items:
                desc = item.description.strip()
                if desc:
                    lines.append(f"• {item.title} — {desc}")
                else:
                    lines.append(f"• {item.title}")
            lines.append("")

        return "\n".join(lines)
