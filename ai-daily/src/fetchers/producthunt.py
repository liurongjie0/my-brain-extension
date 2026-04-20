import httpx

from .base import BaseFetcher, NewsItem


class ProductHuntFetcher(BaseFetcher):
    def __init__(self, max_items: int = 5, access_token: str = ""):
        super().__init__(max_items)
        self.access_token = access_token

    async def fetch(self) -> list[NewsItem]:
        # Product Hunt 需要 OAuth token，这里用简单的 GraphQL 查询
        # 注意：实际使用需要 PH_ACCESS_TOKEN
        query = """
        {
            posts(featured: true, first: %d) {
                edges {
                    node {
                        name
                        tagline
                        url
                        votesCount
                        user {
                            name
                        }
                    }
                }
            }
        }
        """ % self.max_items

        token = self.access_token or ""
        if not token:
            # 没有 token 时返回空列表
            return []

        async with httpx.AsyncClient(timeout=30) as client:
            resp = await client.post(
                "https://api.producthunt.com/v2/api/graphql",
                headers={
                    "Authorization": f"Bearer {token}",
                    "Content-Type": "application/json",
                },
                json={"query": query},
            )

            if resp.status_code != 200:
                return []

            data = resp.json()

        items = []
        edges = data.get("data", {}).get("posts", {}).get("edges", [])
        for edge in edges:
            node = edge.get("node", {})
            title = node.get("name", "")
            url = node.get("url", "")
            description = node.get("tagline", "")
            score = node.get("votesCount", 0)
            author = node.get("user", {}).get("name", "")

            items.append(
                NewsItem(
                    title=title,
                    url=url,
                    source="producthunt",
                    description=description,
                    score=score,
                    author=author,
                )
            )

        return items
