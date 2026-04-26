from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional

from app.models.route import HikingRequest


@dataclass
class ConversationState:
    user_id: str
    group_id: str
    request: HikingRequest = field(default_factory=HikingRequest)
    step: str = "idle"  # idle -> collecting -> confirming -> completed
    created_at: datetime = field(default_factory=datetime.now)
    updated_at: datetime = field(default_factory=datetime.now)
    confirm_count: int = 0
    ask_counts: dict[str, int] = field(default_factory=dict)  # 每个参数的追问次数

    def update(self, request: HikingRequest):
        self.request = request
        self.updated_at = datetime.now()
        if request.is_complete():
            self.step = "confirming"
        else:
            self.step = "collecting"
