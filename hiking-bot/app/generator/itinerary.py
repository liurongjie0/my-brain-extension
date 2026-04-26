from app.models.route import HikingRoute


class ItineraryGenerator:
    def generate(self, route: HikingRoute, date: str = "活动当天") -> str:
        duration = route.duration_hours
        travel_to = 2.0
        travel_back = 2.0

        start_hour = 7
        depart_sh = f"{start_hour:02d}:00"
        arrive_trail = f"{int(start_hour + travel_to):02d}:00"
        hike_start = f"{int(start_hour + travel_to + 0.5):02d}:30"

        hike_end_hour = start_hour + travel_to + 0.5 + duration + 0.5
        hike_end = f"{int(hike_end_hour):02d}:{int((hike_end_hour % 1) * 60):02d}"

        back_sh_hour = hike_end_hour + travel_back
        back_sh = f"{int(back_sh_hour):02d}:{int((back_sh_hour % 1) * 60):02d}"

        return f"""📅 **{date} 行程安排**

| 时间 | 事项 |
|------|------|
| {depart_sh} | 上海集合出发（建议高铁/自驾） |
| {arrive_trail} | 抵达{route.location}，休整、热身 |
| {hike_start} | 开始徒步 |
| {hike_end} | 完成徒步，休整、补给 |
| ~{back_sh} | 返程抵达上海 |

💡 **建议**：
- 提前买好往返车票，周末票紧张
- 起点/终点不一致的话，建议包小车接驳
- 留 1 小时弹性时间，避免赶不上返程"""
