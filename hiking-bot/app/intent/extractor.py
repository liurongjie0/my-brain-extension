import re
from typing import Optional

from app.ai_client import AIClient
from app.models.route import HikingRequest


SYSTEM_PROMPT = """你是一个徒步路线规划助手的参数提取专家。

从用户的自然语言中提取以下参数：
- location: 目的地/地区（如：杭州、苏州、安吉、莫干山）
- people_count: 人数（整数）
- difficulty: 难度偏好，只能是以下之一：休闲、中等、拉练
- distance_km: 期望徒步距离（公里数，如 5、10、15）
- date: 日期（如：这周六、下周日、5月1日）
- notes: 其他备注信息

提取规则：
1. 如果用户提到"轻松""简单""新手""拍照"，difficulty 为"休闲"
2. 如果提到"有点挑战""一般""正常"，difficulty 为"中等"
3. 如果提到"虐""高强度""拉练""训练"，difficulty 为"拉练"
4. 距离提取数字即可，如"十公里左右"→10
5. 如果没有某个参数，返回 null

必须以 JSON 格式返回，不要有任何其他内容。"""


class IntentExtractor:
    def __init__(self):
        self._client = AIClient()

    async def extract(self, message: str) -> HikingRequest:
        if not self._client.is_configured:
            return self._fallback_extract(message)

        try:
            parsed = await self._client.chat_json(SYSTEM_PROMPT, message)
            return self._to_request(parsed)
        except Exception:
            return self._fallback_extract(message)

    def _to_request(self, parsed: dict) -> HikingRequest:
        return HikingRequest(
            location=parsed.get("location"),
            people_count=self._parse_int(parsed.get("people_count")),
            difficulty=parsed.get("difficulty"),
            distance_km=self._parse_float(parsed.get("distance_km")),
            date=parsed.get("date"),
            notes=parsed.get("notes", ""),
        )

    def _fallback_extract(self, message: str) -> HikingRequest:
        req = HikingRequest()
        req.location = self._extract_location(message)
        req.people_count = self._extract_people(message)
        req.difficulty = self._extract_difficulty(message)
        req.distance_km = self._extract_distance(message)
        req.date = self._extract_date(message)
        return req

    def _extract_location(self, text: str) -> Optional[str]:
        if any(w in text for w in ["随便", "你选", "你选择", "都可以", "都行", "推荐", "帮我选"]):
            return "上海周边"

        surrounding_match = re.search(r"(\S+?)[周边附近]", text)
        if surrounding_match:
            city = surrounding_match.group(1)
            if len(city) >= 2:
                return f"{city}周边"

        locations = [
            "上海", "杭州", "苏州", "安吉", "莫干山", "临安", "桐庐", "富阳", "淳安",
            "千岛湖", "天目山", "径山", "西湖", "九溪", "龙井", "徽杭古道",
            "武功山", "黄山", "普陀山", "雁荡山", "天台山", "四明山",
            "德清", "诸暨", "东阳", "余杭", "青浦", "松江", "嘉兴", "宁波",
            "绍兴", "无锡", "常州", "镇江", "南京", "湖州",
        ]
        for loc in locations:
            if loc in text:
                return loc
        return None

    def _extract_people(self, text: str) -> Optional[int]:
        patterns = [
            r"(\d+)\s*个人",
            r"(\d+)\s*人",
            r"(\d+)\s*个",
            r"我们(\d+)个",
        ]
        for p in patterns:
            m = re.search(p, text)
            if m:
                return int(m.group(1))
        return None

    def _extract_difficulty(self, text: str) -> Optional[str]:
        if any(w in text for w in ["休闲", "轻松", "简单", "新手", "拍照", "散步"]):
            return "休闲"
        if any(w in text for w in ["拉练", "虐", "高强度", "训练", "挑战"]):
            return "拉练"
        if any(w in text for w in ["中等", "一般", "正常", "有点挑战"]):
            return "中等"
        return None

    def _extract_distance(self, text: str) -> Optional[float]:
        patterns = [
            r"(\d+(?:\.\d+)?)\s*公里",
            r"(\d+(?:\.\d+)?)\s*km",
            r"(\d+(?:\.\d+)?)\s*千米",
            r"(\d+(?:\.\d+)?)\s*公里左右",
            r"(\d+(?:\.\d+)?)\s*km左右",
        ]
        for p in patterns:
            m = re.search(p, text, re.IGNORECASE)
            if m:
                return float(m.group(1))
        word_map = {"五": 5, "十": 10, "十五": 15, "二十": 20}
        for word, val in word_map.items():
            if f"{word}公里" in text or f"{word}km" in text:
                return float(val)
        return None

    def _extract_date(self, text: str) -> Optional[str]:
        patterns = [
            r"这(周六|周日|周末|周[一二三四五])",
            r"下(周六|周日|周末|周[一二三四五])",
            r"(\d{1,2}月\d{1,2}[日号])",
        ]
        for p in patterns:
            m = re.search(p, text)
            if m:
                return m.group(0)
        if "明天" in text:
            return "明天"
        if "后天" in text:
            return "后天"
        return None

    @staticmethod
    def _parse_int(v) -> Optional[int]:
        if v is None:
            return None
        try:
            return int(v)
        except (ValueError, TypeError):
            return None

    @staticmethod
    def _parse_float(v) -> Optional[float]:
        if v is None:
            return None
        try:
            return float(v)
        except (ValueError, TypeError):
            return None
