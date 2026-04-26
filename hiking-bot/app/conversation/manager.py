import asyncio
from datetime import datetime, timedelta
from typing import Optional

from app.conversation.states import ConversationState
from app.generator.checklist import ChecklistGenerator
from app.generator.itinerary import ItineraryGenerator
from app.generator.route_card import RouteGenerator
from app.intent.extractor import IntentExtractor
from app.models.history import HistoryStore
from app.models.route import HikingRequest


class ConversationManager:
    def __init__(self):
        self._states: dict[str, ConversationState] = {}
        self._extractor = IntentExtractor()
        self._route_generator = RouteGenerator()
        self._itinerary_generator = ItineraryGenerator()
        self._checklist_generator = ChecklistGenerator()
        self._history = HistoryStore()
        self._lock = asyncio.Lock()

    def _key(self, user_id: str, group_id: str) -> str:
        return f"{group_id}:{user_id}"

    def _cleanup(self):
        now = datetime.now()
        ttl = timedelta(seconds=86400)
        expired = [
            k for k, v in self._states.items()
            if now - v.updated_at > ttl
        ]
        for k in expired:
            del self._states[k]

    async def handle(self, user_id: str, group_id: str, message: str) -> str:
        async with self._lock:
            self._cleanup()
            key = self._key(user_id, group_id)

            # 新对话或已完成的对话
            if key not in self._states or self._states[key].step == "completed":
                # 检查是否是"换"——从历史记录恢复参数重新推荐
                if self._states.get(key) and self._states[key].step == "completed" and any(w in message for w in ["换", "再来", "重新"]):
                    history = self._history.get_recent(group_id, limit=1)
                    if history:
                        import json
                        route_data = json.loads(history[0]["route_data"])
                        state = ConversationState(
                            user_id=user_id,
                            group_id=group_id,
                            request=HikingRequest(
                                location=route_data.get("location"),
                                people_count=route_data.get("distance_km") and int(route_data.get("distance_km", 0)) or None,
                                difficulty=route_data.get("difficulty"),
                                distance_km=route_data.get("distance_km"),
                            ),
                        )
                        state.step = "collecting"
                        self._states[key] = state
                        return "好的，告诉我你想调整什么？（地点/距离/难度/人数）"

                extracted = await self._extractor.extract(message)
                state = ConversationState(user_id=user_id, group_id=group_id, request=extracted)

                if extracted.is_complete():
                    state.step = "confirming"
                    self._states[key] = state
                    return await self._generate_response(state)
                else:
                    state.step = "collecting"
                    self._states[key] = state
                    return self._ask_for_missing(state)

            # 继续已有对话
            state = self._states[key]

            # 检查是否是确认/否定
            lower_msg = message.lower()
            if state.step == "confirming":
                if any(w in lower_msg for w in ["好", "行", "可以", "保存", "要", "ok", "yes", "是"]):
                    # 保存并结束
                    route = await self._route_generator.generate(state.request)
                    itinerary = self._itinerary_generator.generate(route, state.request.date or "活动当天")
                    checklist = self._checklist_generator.generate(route, state.request)
                    self._history.save(group_id, route, itinerary, checklist)
                    state.step = "completed"

                    route_card = self._route_generator.format_card(route)
                    return f"{route_card}\n\n{itinerary}\n\n{checklist}\n\n✅ 已保存到历史记录！"
                elif any(w in lower_msg for w in ["不", "换", "重新", "no", "再来"]):
                    state.step = "collecting"
                    state.confirm_count += 1
                    return "好的，告诉我你想调整什么？（地点/距离/难度/人数）"
                else:
                    # 可能是补充信息，重新提取
                    extracted = await self._extractor.extract(message)
                    if any([
                        extracted.location and extracted.location != state.request.location,
                        extracted.difficulty and extracted.difficulty != state.request.difficulty,
                        extracted.distance_km and extracted.distance_km != state.request.distance_km,
                        extracted.people_count and extracted.people_count != state.request.people_count,
                    ]):
                        # 有变化，更新请求
                        if extracted.location:
                            state.request.location = extracted.location
                        if extracted.difficulty:
                            state.request.difficulty = extracted.difficulty
                        if extracted.distance_km:
                            state.request.distance_km = extracted.distance_km
                        if extracted.people_count:
                            state.request.people_count = extracted.people_count
                        state.step = "confirming"
                        return await self._generate_response(state)
                    else:
                        return '已帮你生成路线，要保存这条吗？（回复"好"保存，"换"重新推荐）'

            # 收集信息阶段
            extracted = await self._extractor.extract(message)

            # 上下文感知：根据当前缺失的参数，对短回答做特殊解析
            missing = state.request.missing_fields()
            if any("人数" in m for m in missing):
                # 尝试把纯数字解析为人数
                people = self._parse_people_from_context(message)
                if people:
                    extracted.people_count = people
            if any("难度" in m for m in missing):
                # 尝试从短回答解析难度
                diff = self._parse_difficulty_from_context(message)
                if diff:
                    extracted.difficulty = diff
            if any("距离" in m for m in missing) and not any("人数" in m for m in missing):
                # 只有在不缺失人数时，才把纯数字解析为距离
                dist = self._parse_distance_from_context(message)
                if dist:
                    extracted.distance_km = dist

            if extracted.location:
                state.request.location = extracted.location
            if extracted.difficulty:
                state.request.difficulty = extracted.difficulty
            if extracted.distance_km:
                state.request.distance_km = extracted.distance_km
            if extracted.people_count:
                state.request.people_count = extracted.people_count
            if extracted.date:
                state.request.date = extracted.date
            if extracted.notes:
                state.request.notes = extracted.notes

            state.updated_at = datetime.now()

            if state.request.is_complete():
                state.step = "confirming"
                return await self._generate_response(state)
            else:
                return self._ask_for_missing(state)

    def _parse_people_from_context(self, message: str) -> Optional[int]:
        """在追问人数时，把纯数字或简短回答解析为人数"""
        message = message.strip()
        import re
        # 纯数字
        if re.match(r"^\d+$", message):
            return int(message)
        # "X个""X个人"
        m = re.match(r"^(\d+)\s*(?:个人|人|个)?$", message)
        if m:
            return int(m.group(1))
        return None

    def _parse_difficulty_from_context(self, message: str) -> Optional[str]:
        """在追问难度时，把简短回答映射为难度"""
        msg = message.strip().lower()
        if any(w in msg for w in ["休闲", "轻松", "简单", "新手", "拍照", "散步", "缓", "慢"]):
            return "休闲"
        if any(w in msg for w in ["拉练", "虐", "高强度", "训练", "挑战", "猛", "硬"]):
            return "拉练"
        if any(w in msg for w in ["中等", "一般", "正常", "有点挑战", "适中", " medium"]):
            return "中等"
        if any(w in msg for w in ["随便", "都行", "都可以", "你选", "推荐"]):
            return "中等"
        return None

    def _parse_distance_from_context(self, message: str) -> Optional[float]:
        """在追问距离时，把纯数字或简短回答解析为距离"""
        message = message.strip()
        import re
        # 纯数字
        if re.match(r"^\d+$", message):
            return float(message)
        # "X公里""Xkm"
        m = re.match(r"^(\d+(?:\.\d+)?)\s*(?:公里|km|千米)?$", message, re.IGNORECASE)
        if m:
            return float(m.group(1))
        return None

    def _ask_for_missing(self, state: ConversationState) -> str:
        missing = state.request.missing_fields()
        if not missing:
            return "信息已收齐，正在生成路线..."

        # 用中文关键词匹配缺失字段
        field_map = [
            ("目的地", "location", "目的地", ["上海周边", "杭州周边", "苏州周边"]),
            ("人数", "people_count", "人数", [3, 5, 8]),
            ("难度", "difficulty", "难度偏好", ["中等"]),
            ("距离", "distance_km", "期望距离", [10.0]),
        ]

        for keyword, field_name, label, defaults in field_map:
            if any(keyword in m for m in missing):
                count = state.ask_counts.get(field_name, 0)
                state.ask_counts[field_name] = count + 1

                if count >= 1:
                    # 追问过 2 次仍无有效回答，自动给默认值
                    default = defaults[0]
                    if field_name == "location":
                        state.request.location = default
                    elif field_name == "people_count":
                        state.request.people_count = default
                    elif field_name == "difficulty":
                        state.request.difficulty = default
                    elif field_name == "distance_km":
                        state.request.distance_km = default

                    if state.request.is_complete():
                        return "我没太听懂，帮你选了默认的，直接生成路线啦！"

                    remaining = [l for k, f, l, d in field_map
                                 if any(k in m for m in state.request.missing_fields())]
                    next_label = remaining[0] if remaining else "确认"
                    return f"{label}我帮你选了默认的，还差：{next_label}"

                # 第一次追问，给出友好提示
                if count == 1:
                    hints = {
                        "目的地": '（比如：杭州、苏州、安吉，或者说"随便"）',
                        "人数": "（比如：3、5、8）",
                        "难度偏好": '（休闲/中等/拉练，或者说"随便"）',
                        "期望距离": "（比如：5km、10km、15km）",
                    }
                    return f"收到！还差一个信息：{label}{hints.get(label, '')}"

                return f"收到！还差一个信息：{label}？"

        return "收到！还差几个信息：" + "、".join(missing)

    async def _generate_response(self, state: ConversationState) -> str:
        route = await self._route_generator.generate(state.request)
        route_card = self._route_generator.format_card(route)
        itinerary = self._itinerary_generator.generate(route, state.request.date or "活动当天")
        checklist = self._checklist_generator.generate(route, state.request)

        return (
            f"帮你找到这条路线：\n\n"
            f"{route_card}\n\n"
            f"{itinerary}\n\n"
            f"{checklist}\n\n"
            '💾 满意的话回复"好"，我帮你保存记录；'
            '不满意回复"换"，我重新推荐。'
        )

    def get_state(self, user_id: str, group_id: str) -> Optional[ConversationState]:
        return self._states.get(self._key(user_id, group_id))
