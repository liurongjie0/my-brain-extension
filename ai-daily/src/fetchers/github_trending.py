import httpx
from bs4 import BeautifulSoup

from .base import BaseFetcher, NewsItem


class GitHubTrendingFetcher(BaseFetcher):
    def __init__(self, max_items: int = 5, languages: list[str] = None):
        super().__init__(max_items)
        self.languages = languages or []

    async def fetch(self) -> list[NewsItem]:
        url = "https://github.com/trending"
        if self.languages:
            url += "?language=" + self.languages[0]

        async with httpx.AsyncClient(timeout=30, follow_redirects=True) as client:
            resp = await client.get(
                url,
                headers={
                    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"
                },
            )
            resp.raise_for_status()

        soup = BeautifulSoup(resp.text, "html.parser")
        items = []

        article_boxes = soup.find_all("article", class_="Box-row", limit=self.max_items)
        for box in article_boxes:
            a_tag = box.find("h2", class_="h3").find("a")
            if not a_tag:
                continue

            href = a_tag.get("href", "")
            title = href.strip("/")
            full_url = f"https://github.com{href}"

            desc_tag = box.find("p", class_="col-9")
            description = desc_tag.get_text(strip=True) if desc_tag else ""

            star_tag = box.find("a", class_="Link--muted d-inline-block mr-3")
            stars = star_tag.get_text(strip=True).replace(",", "") if star_tag else "0"
            try:
                score = int(stars)
            except ValueError:
                score = 0

            items.append(
                NewsItem(
                    title=title,
                    url=full_url,
                    source="github_trending",
                    description=description,
                    score=score,
                )
            )

        return items
