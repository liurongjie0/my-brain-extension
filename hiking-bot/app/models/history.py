import json
import sqlite3
from datetime import datetime
from pathlib import Path

from app.config import Config
from app.models.route import HikingRoute


class HistoryStore:
    def __init__(self):
        Path(Config.DATA_DIR).mkdir(parents=True, exist_ok=True)
        self._init_db()

    def _init_db(self):
        with sqlite3.connect(Config.DATABASE_PATH) as conn:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS hiking_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    group_id TEXT NOT NULL,
                    route_name TEXT NOT NULL,
                    route_data TEXT NOT NULL,
                    itinerary TEXT,
                    checklist TEXT,
                    created_at TEXT NOT NULL
                )
            """)
            conn.execute("""
                CREATE TABLE IF NOT EXISTS route_cache (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    location TEXT NOT NULL,
                    difficulty TEXT,
                    distance_range TEXT,
                    route_data TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
            """)

    def save(self, group_id: str, route: HikingRoute, itinerary: str, checklist: str) -> int:
        with sqlite3.connect(Config.DATABASE_PATH) as conn:
            cursor = conn.execute(
                """
                INSERT INTO hiking_history (group_id, route_name, route_data, itinerary, checklist, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (
                    group_id,
                    route.name,
                    json.dumps(route.to_dict(), ensure_ascii=False),
                    itinerary,
                    checklist,
                    datetime.now().isoformat(),
                ),
            )
            return cursor.lastrowid

    def get_recent(self, group_id: str, limit: int = 5) -> list[dict]:
        with sqlite3.connect(Config.DATABASE_PATH) as conn:
            conn.row_factory = sqlite3.Row
            rows = conn.execute(
                """
                SELECT * FROM hiking_history
                WHERE group_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """,
                (group_id, limit),
            ).fetchall()
            return [dict(row) for row in rows]
