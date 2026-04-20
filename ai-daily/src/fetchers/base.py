from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass


@dataclass
class NewsItem:
    title: str
    url: str
    source: str
    description: str = ""
    score: int = 0
    author: str = ""
    published: str = ""


class BaseFetcher(ABC):
    def __init__(self, max_items: int = 5):
        self.max_items = max_items

    @abstractmethod
    async def fetch(self) -> list[NewsItem]:
        pass
