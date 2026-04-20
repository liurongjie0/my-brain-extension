import datetime

import feedparser

from .base import BaseFetcher, NewsItem


class ArxivFetcher(BaseFetcher):
    def __init__(self, max_items: int = 5, categories: list[str] = None):
        super().__init__(max_items)
        self.categories = categories or ["cs.AI", "cs.CL", "cs.LG"]

    async def fetch(self) -> list[NewsItem]:
        items = []

        for cat in self.categories:
            if len(items) >= self.max_items:
                break

            url = (
                f"http://export.arxiv.org/api/query?"
                f"search_query=cat:{cat}&sortBy=submittedDate&sortOrder=descending&max_results={self.max_items}"
            )

            feed = feedparser.parse(url)

            for entry in feed.entries:
                if len(items) >= self.max_items:
                    break

                title = entry.get("title", "").replace("\n", " ").strip()
                link = entry.get("link", "")
                summary = entry.get("summary", "")[:500]
                authors = ", ".join(a.get("name", "") for a in entry.get("authors", []))
                published = entry.get("published", "")[:10]

                items.append(
                    NewsItem(
                        title=title,
                        url=link,
                        source="arxiv",
                        description=summary,
                        author=authors,
                        published=published,
                    )
                )

        return items[: self.max_items]
