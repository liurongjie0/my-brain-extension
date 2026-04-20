import feedparser

from .base import BaseFetcher, NewsItem


class TechCrunchFetcher(BaseFetcher):
    def __init__(self, max_items: int = 5, tags: list[str] = None):
        super().__init__(max_items)
        self.tags = tags or ["artificial-intelligence"]

    async def fetch(self) -> list[NewsItem]:
        items = []

        for tag in self.tags:
            if len(items) >= self.max_items:
                break

            url = f"https://techcrunch.com/category/{tag}/feed/"
            feed = feedparser.parse(url)

            for entry in feed.entries:
                if len(items) >= self.max_items:
                    break

                title = entry.get("title", "").strip()
                link = entry.get("link", "")
                description = entry.get("summary", "")[:300]
                author = entry.get("author", "")
                published = entry.get("published", "")[:10]

                items.append(
                    NewsItem(
                        title=title,
                        url=link,
                        source="techcrunch",
                        description=description,
                        author=author,
                        published=published,
                    )
                )

        return items[: self.max_items]
