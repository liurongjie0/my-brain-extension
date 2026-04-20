import feedparser

from .base import BaseFetcher, NewsItem


class AnthropicFetcher(BaseFetcher):
    def __init__(self, max_items: int = 5):
        super().__init__(max_items)

    async def fetch(self) -> list[NewsItem]:
        url = "https://www.anthropic.com/rss.xml"
        feed = feedparser.parse(url)

        items = []
        for entry in feed.entries[: self.max_items]:
            title = entry.get("title", "").strip()
            link = entry.get("link", "")
            description = entry.get("summary", "")[:300]
            author = entry.get("author", "")
            published = entry.get("published", "")[:10]

            items.append(
                NewsItem(
                    title=title,
                    url=link,
                    source="anthropic",
                    description=description,
                    author=author,
                    published=published,
                )
            )

        return items
