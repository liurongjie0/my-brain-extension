from app.models.route import HikingRoute, HikingRequest


CHECKLIST_DATA = {
    "personal_base": [
        "登山鞋 / 防滑运动鞋",
        "速干衣裤（长袖长裤防刮）",
        "备用袜子 1 双",
        "背包 20-30L",
    ],
    "personal_food": [
        "饮用水 2L+（按天气增减）",
        "路餐 / 能量棒 / 巧克力",
        "电解质饮料",
    ],
    "personal_gear": [
        "登山杖（建议双杖）",
        "雨衣 / 冲锋衣",
        "帽子 / 魔术头巾",
        "防晒霜 / 墨镜",
    ],
    "personal_safety": [
        "手机（提前下好轨迹）",
        "充电宝",
        "头灯（备用）",
        "个人药品",
    ],
    "team_gear": [
        "急救包（绷带/碘伏/创可贴/止痛药）",
        "对讲机（山区信号不稳定）",
        "垃圾袋（无痕山野）",
        "保温毯（应急用）",
    ],
    "team_food": [
        "水果（西瓜/橘子，分享用）",
        "保温壶装热饮（冬季）",
    ],
}

DIFFICULTY_EXTRAS = {
    "休闲": {
        "personal": ["相机/手机拍照", "休闲零食"],
        "team": [],
    },
    "中等": {
        "personal": ["护膝（下坡保护）"],
        "team": ["绳索（应急）"],
    },
    "拉练": {
        "personal": ["护膝", "肌贴", "盐丸/能量胶"],
        "team": ["绳索", "哨子", "备用头灯"],
    },
}


class ChecklistGenerator:
    def generate(self, route: HikingRoute, request: HikingRequest) -> str:
        extras = DIFFICULTY_EXTRAS.get(route.difficulty, {"personal": [], "team": []})

        personal_items = (
            CHECKLIST_DATA["personal_base"]
            + CHECKLIST_DATA["personal_food"]
            + CHECKLIST_DATA["personal_gear"]
            + CHECKLIST_DATA["personal_safety"]
            + extras["personal"]
        )

        team_items = (
            CHECKLIST_DATA["team_gear"]
            + CHECKLIST_DATA["team_food"]
            + extras["team"]
        )

        personal_text = "\n".join(f"  □ {item}" for item in personal_items)
        team_text = "\n".join(f"  □ {item}" for item in team_items)

        if request.people_count:
            team_header = f"团队公共物资（{request.people_count}人）"
        else:
            team_header = "团队公共物资"

        return f"""🎒 **装备清单**

👤 **个人必备**
{personal_text}

👥 **{team_header}**
{team_text}

⚠️ **特别提醒**
- 根据天气预报增减衣物
- 垃圾全部带下山，践行无痕山野
- 建议出发前互留紧急联系人"""
