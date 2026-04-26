import json

from app.ai_client import AIClient
from app.models.route import HikingRoute, HikingRequest


SYSTEM_PROMPT = """你是江浙沪地区徒步路线专家。根据用户的需求，推荐一条合适的单日徒步路线。

你必须以 JSON 格式返回，字段如下：
{
    "name": "路线名称",
    "location": "具体位置",
    "distance_km": 数字（如 10.5）,
    "elevation_m": 累计爬升（整数，如 450）,
    "difficulty": "休闲"或"中等"或"拉练",
    "duration_hours": 预计用时（如 4.5）,
    "description": "路线描述，100字左右",
    "highlights": ["亮点1", "亮点2", "亮点3"],
    "twoloo_url": "",
    "xiaohongshu_url": ""
}

注意：
1. 路线必须是真实存在的上海周边单日徒步路线
2. 难度要和用户要求匹配
3. 距离尽量接近用户要求
4. 不要编造不存在的路线
5. 只返回 JSON，不要其他内容"""

# 上海周边经典路线知识库（作为 fallback）
SHANGHAI_ROUTES = [
    {
        "name": "九溪十八涧环线",
        "location": "杭州西湖区",
        "distance_km": 10.5,
        "elevation_m": 450,
        "difficulty": "中等",
        "duration_hours": 4.5,
        "description": "从龙井村出发，穿越九溪烟树，沿十八涧溪流而上，经十里琅珰俯瞰西湖，是一条经典的杭州徒步路线。",
        "highlights": ["九溪烟树", "龙井茶园", "十里琅珰远眺"],
    },
    {
        "name": "莫干山蒋公道",
        "location": "湖州德清",
        "distance_km": 8.0,
        "elevation_m": 500,
        "difficulty": "中等",
        "duration_hours": 4.0,
        "description": "莫干山经典徒步线，从庾村出发，经剑池、芦花荡，一路竹林掩映，可参观民国别墅群。",
        "highlights": ["竹林古道", "剑池飞瀑", "民国别墅"],
    },
    {
        "name": "西湖群山标毅线",
        "location": "杭州西湖区",
        "distance_km": 25.0,
        "elevation_m": 1200,
        "difficulty": "拉练",
        "duration_hours": 8.0,
        "description": "西湖群山标准毅行线路，贯穿天竺山、十里琅珰、五云山、虎跑等，是杭州最具挑战性的单日线路之一。",
        "highlights": ["全程山脊线", "俯瞰西湖全景", "茶园竹林"],
    },
    {
        "name": "佘山森林公园",
        "location": "上海松江",
        "distance_km": 5.0,
        "elevation_m": 100,
        "difficulty": "休闲",
        "duration_hours": 2.0,
        "description": "上海唯一的自然山林，东佘山、西佘山环线，适合新手和亲子，可参观佘山天文台和天主教堂。",
        "highlights": ["佘山天文台", "天主教堂", "轻松休闲"],
    },
    {
        "name": "淀山湖环湖",
        "location": "上海青浦",
        "distance_km": 12.0,
        "elevation_m": 50,
        "difficulty": "休闲",
        "duration_hours": 3.5,
        "description": "沿淀山湖畔徒步，途经大观园、东方绿舟，湖光山色，路况平坦，适合休闲散步。",
        "highlights": ["淀山湖景", "大观园", "路况平坦"],
    },
    {
        "name": "徽杭古道反穿",
        "location": "安徽宣城/杭州临安",
        "distance_km": 15.0,
        "elevation_m": 800,
        "difficulty": "中等",
        "duration_hours": 6.0,
        "description": "中国十大经典古道之一，从临安清凉峰镇到安徽绩溪，穿越峡谷溪流，体验千年商旅古道。",
        "highlights": ["千年古道", "江南第一关", "峡谷溪流"],
    },
    {
        "name": "径山古道",
        "location": "杭州余杭",
        "distance_km": 6.0,
        "elevation_m": 350,
        "difficulty": "休闲",
        "duration_hours": 3.0,
        "description": "从径山村出发，沿千年古道登径山寺，竹林掩映，是 Japanese 茶道发源地，文化气息浓厚。",
        "highlights": ["径山寺", "竹林古道", "茶文化"],
    },
    {
        "name": "大明山登山线",
        "location": "杭州临安",
        "distance_km": 10.0,
        "elevation_m": 900,
        "difficulty": "中等",
        "duration_hours": 5.0,
        'description': '浙江十大名山之一，有"浙江小黄山"之称，奇峰怪石、悬崖栈道，风景壮丽。',
        "highlights": ["悬空栈道", "高山草甸", "奇松怪石"],
    },
    {
        "name": "龙王山环线",
        "location": "湖州安吉",
        "distance_km": 13.0,
        "elevation_m": 1100,
        "difficulty": "拉练",
        "duration_hours": 7.0,
        "description": "天目七尖之一，浙北第一高峰，海拔1587米，路线原始，适合有一定经验的徒步者。",
        "highlights": ["浙北最高峰", "原始森林", "山脊行走"],
    },
    {
        "name": "东白山环线",
        "location": "绍兴诸暨/东阳",
        "distance_km": 14.0,
        "elevation_m": 1000,
        "difficulty": "拉练",
        "duration_hours": 6.5,
        "description": "浙中名山，海拔1194米，风车草甸、高山茶园，日出云海极具观赏性。",
        "highlights": ["风车草甸", "高山茶园", "云海日出"],
    },
]


class RouteGenerator:
    def __init__(self):
        self._client = AIClient()

    async def generate(self, request: HikingRequest) -> HikingRoute:
        if not self._client.is_configured:
            return self._fallback_generate(request)

        prompt = self._build_prompt(request)
        try:
            parsed = await self._client.chat_json(SYSTEM_PROMPT, prompt)
            if not parsed:
                return self._fallback_generate(request)
            return HikingRoute(
                name=parsed.get("name", "未知路线"),
                location=parsed.get("location", request.location or ""),
                distance_km=float(parsed.get("distance_km", 0)),
                elevation_m=int(parsed.get("elevation_m", 0)),
                difficulty=parsed.get("difficulty", "中等"),
                duration_hours=float(parsed.get("duration_hours", 0)),
                description=parsed.get("description", ""),
                highlights=parsed.get("highlights", []),
                twoloo_url=parsed.get("twoloo_url", ""),
                xiaohongshu_url=parsed.get("xiaohongshu_url", ""),
            )
        except Exception:
            return self._fallback_generate(request)

    def _build_prompt(self, request: HikingRequest) -> str:
        parts = ["请推荐一条上海周边单日徒步路线。"]
        if request.location:
            parts.append(f"目的地：{request.location}")
        if request.distance_km:
            parts.append(f"期望距离：约{request.distance_km}公里")
        if request.difficulty:
            parts.append(f"难度偏好：{request.difficulty}")
        if request.people_count:
            parts.append(f"人数：{request.people_count}人")
        if request.date:
            parts.append(f"日期：{request.date}")
        if request.notes:
            parts.append(f"备注：{request.notes}")
        return "\n".join(parts)

    def _fallback_generate(self, request: HikingRequest) -> HikingRoute:
        candidates = SHANGHAI_ROUTES
        if request.location:
            candidates = [r for r in candidates if request.location in r["location"]]
        if request.difficulty:
            candidates = [r for r in candidates if r["difficulty"] == request.difficulty]
        if request.distance_km:
            candidates.sort(key=lambda r: abs(r["distance_km"] - request.distance_km))

        if candidates:
            data = candidates[0]
        else:
            data = SHANGHAI_ROUTES[0]

        return HikingRoute(
            name=data["name"],
            location=data["location"],
            distance_km=data["distance_km"],
            elevation_m=data["elevation_m"],
            difficulty=data["difficulty"],
            duration_hours=data["duration_hours"],
            description=data["description"],
            highlights=data["highlights"],
        )

    def format_card(self, route: HikingRoute) -> str:
        import urllib.parse
        stars = "⭐" * ({"休闲": 1, "中等": 2, "拉练": 3}.get(route.difficulty, 2))
        highlights = "\n".join(f"  • {h}" for h in route.highlights) if route.highlights else ""
        query = urllib.parse.quote(route.name)

        return f"""🏔️ **{route.name}**
📍 {route.location}
📏 距离：{route.distance_km}km  |  ⛰️ 爬升：{route.elevation_m}m
💪 难度：{route.difficulty} {stars}  |  ⏱️ 预估：{route.duration_hours}小时

{route.description}

✨ 亮点：
{highlights}

🔍 [两步路搜索](https://www.2bulu.com/track/search-{query}.htm)  |  [小红书搜索](https://www.xiaohongshu.com/search_result?keyword={query})"""
