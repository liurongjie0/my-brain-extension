import httpx


class WeChatBot:
    def __init__(self, webhook_url: str):
        self.webhook_url = webhook_url

    async def send_text(self, content: str) -> bool:
        if not self.webhook_url or "YOUR_KEY" in self.webhook_url:
            print("[WeChatBot] Webhook URL 未配置，跳过推送")
            return False

        payload = {
            "msgtype": "markdown",
            "markdown": {"content": content},
        }

        async with httpx.AsyncClient(timeout=30) as client:
            try:
                resp = await client.post(self.webhook_url, json=payload)
                resp.raise_for_status()
                data = resp.json()
                if data.get("errcode") == 0:
                    print("[WeChatBot] 推送成功")
                    return True
                else:
                    print(f"[WeChatBot] 推送失败: {data}")
                    return False
            except Exception as e:
                print(f"[WeChatBot] 推送异常: {e}")
                return False
