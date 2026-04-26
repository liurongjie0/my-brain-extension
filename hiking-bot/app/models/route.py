from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional


@dataclass
class HikingRoute:
    name: str
    location: str
    distance_km: float
    elevation_m: int
    difficulty: str
    duration_hours: float
    description: str = ""
    highlights: list[str] = field(default_factory=list)
    twoloo_url: str = ""
    xiaohongshu_url: str = ""
    created_at: datetime = field(default_factory=datetime.now)

    def to_dict(self) -> dict:
        return {
            "name": self.name,
            "location": self.location,
            "distance_km": self.distance_km,
            "elevation_m": self.elevation_m,
            "difficulty": self.difficulty,
            "duration_hours": self.duration_hours,
            "description": self.description,
            "highlights": self.highlights,
            "twoloo_url": self.twoloo_url,
            "xiaohongshu_url": self.xiaohongshu_url,
            "created_at": self.created_at.isoformat(),
        }


@dataclass
class HikingRequest:
    location: Optional[str] = None
    people_count: Optional[int] = None
    difficulty: Optional[str] = None
    distance_km: Optional[float] = None
    date: Optional[str] = None
    notes: str = ""

    def is_complete(self) -> bool:
        return all([
            self.location,
            self.people_count,
            self.difficulty,
            self.distance_km,
        ])

    def missing_fields(self) -> list[str]:
        fields = []
        if not self.location:
            fields.append("目的地（如：杭州、苏州、安吉）")
        if not self.people_count:
            fields.append("人数")
        if not self.difficulty:
            fields.append("难度偏好（休闲/中等/拉练）")
        if not self.distance_km:
            fields.append("期望距离（如：5km、10km、15km）")
        return fields
