import hashlib
import sqlite3
from pathlib import Path

from .fetchers.base import NewsItem


class Aggregator:
    def __init__(self, db_path: str = "data/daily.db"):
        self.db_path = Path(db_path)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._init_db()

    def _init_db(self) -> None:
        with sqlite3.connect(self.db_path) as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS pushed (
                    url_hash TEXT PRIMARY KEY,
                    url TEXT NOT NULL,
                    title TEXT,
                    source TEXT,
                    pushed_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
                """
            )

    def _hash(self, url: str) -> str:
        return hashlib.sha256(url.encode()).hexdigest()[:16]

    def filter_new(self, items: list[NewsItem]) -> list[NewsItem]:
        new_items = []
        with sqlite3.connect(self.db_path) as conn:
            for item in items:
                h = self._hash(item.url)
                cursor = conn.execute("SELECT 1 FROM pushed WHERE url_hash = ?", (h,))
                if cursor.fetchone() is None:
                    new_items.append(item)
        return new_items

    def mark_pushed(self, items: list[NewsItem]) -> None:
        with sqlite3.connect(self.db_path) as conn:
            for item in items:
                h = self._hash(item.url)
                conn.execute(
                    "INSERT OR IGNORE INTO pushed (url_hash, url, title, source) VALUES (?, ?, ?, ?)",
                    (h, item.url, item.title, item.source),
                )
            conn.commit()

    def get_stats(self) -> dict:
        with sqlite3.connect(self.db_path) as conn:
            total = conn.execute("SELECT COUNT(*) FROM pushed").fetchone()[0]
            today = conn.execute(
                "SELECT COUNT(*) FROM pushed WHERE date(pushed_at) = date('now')"
            ).fetchone()[0]
            return {"total": total, "today": today}
