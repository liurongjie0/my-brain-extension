import asyncio

import httpx

from .base import BaseFetcher, NewsItem


class HackerNewsFetcher(BaseFetcher):
    def __init__(self, max_items: int = 5, min_score: int = 100):
        super().__init__(max_items)
        self.min_score = min_score

    async def fetch(self) -> list[NewsItem]:
        async with httpx.AsyncClient(timeout=30) as client:
            try:
                top_resp = await client.get(
                    "https://hacker-news.firebaseio.com/v0/topstories.json"
                )
                top_resp.raise_for_status()
                top_ids = top_resp.json()[: self.max_items * 3]
            except Exception as e:
                print(f"[hackernews] 获取热门列表失败: {e}")
                return []

            tasks = [self._fetch_item(client, item_id) for item_id in top_ids]
            results = await asyncio.gather(*tasks, return_exceptions=True)

        items = []
        for result in results:
            if isinstance(result, Exception):
                continue
            if result and result.score >= self.min_score:
                items.append(result)
            if len(items) >= self.max_items:
                break

        return items

    async def _fetch_item(self, client: httpx.AsyncClient, item_id: int):
        try:
            resp = await client.get(
                f"https://hacker-news.firebaseio.com/v0/item/{item_id}.json"
            )
            resp.raise_for_status()
            data = resp.json()

            if not data:
                return None

            title = data.get("title", "")
            url = data.get("url", f"https://news.ycombinator.com/item?id={item_id}")
            score = data.get("score", 0)
            author = data.get("by", "")

            return NewsItem(
                title=title,
                url=url,
                source="hackernews",
                score=score,
                author=author,
            )
        except Exception:
            return None
