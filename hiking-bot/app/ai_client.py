import json

import httpx

from app.config import Config


class AIClient:
    """统一 AI 客户端，支持 OpenAI 和 Anthropic Messages API 格式"""

    def __init__(self):
        self.api_key = Config.AI_API_KEY
        self.base_url = Config.AI_BASE_URL.rstrip("/")
        self.model = Config.AI_MODEL
        self._is_anthropic = "kimi.com/coding" in self.base_url

    async def chat(self, system_prompt: str, user_prompt: str, json_mode: bool = False) -> str:
        """发送聊天请求，返回文本内容"""
        if not self.api_key:
            return ""

        if self._is_anthropic:
            return await self._chat_anthropic(system_prompt, user_prompt)
        else:
            return await self._chat_openai(system_prompt, user_prompt, json_mode)

    async def _chat_openai(self, system_prompt: str, user_prompt: str, json_mode: bool) -> str:
        messages = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        messages.append({"role": "user", "content": user_prompt})

        payload = {
            "model": self.model,
            "messages": messages,
        }
        if json_mode:
            payload["response_format"] = {"type": "json_object"}

        async with httpx.AsyncClient(timeout=60) as client:
            resp = await client.post(
                f"{self.base_url}/chat/completions",
                headers={"Authorization": f"Bearer {self.api_key}"},
                json=payload,
            )
            resp.raise_for_status()
            data = resp.json()
            return data["choices"][0]["message"]["content"]

    async def _chat_anthropic(self, system_prompt: str, user_prompt: str) -> str:
        """Anthropic Messages API（Kimi Coding 等使用此格式）"""
        content = user_prompt
        if system_prompt:
            content = f"{system_prompt}\n\n{user_prompt}"

        # Kimi Coding 的 messages endpoint 需要 /v1 前缀
        api_base = self.base_url
        if api_base.endswith("/coding"):
            api_base = f"{api_base}/v1"

        async with httpx.AsyncClient(timeout=60) as client:
            resp = await client.post(
                f"{api_base}/messages",
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": self.model,
                    "max_tokens": 4096,
                    "messages": [
                        {"role": "user", "content": content},
                    ],
                },
            )
            resp.raise_for_status()
            data = resp.json()
            return data["content"][0]["text"]

    async def chat_json(self, system_prompt: str, user_prompt: str) -> dict:
        """发送聊天请求，返回解析后的 JSON"""
        content = await self.chat(system_prompt, user_prompt, json_mode=True)
        if not content:
            return {}

        # 尝试提取 JSON（有些模型会在 markdown code block 中返回 JSON）
        text = content.strip()
        if text.startswith("```json"):
            text = text[7:]
        elif text.startswith("```"):
            text = text[3:]
        if text.endswith("```"):
            text = text[:-3]
        text = text.strip()

        try:
            return json.loads(text)
        except json.JSONDecodeError:
            return {}

    @property
    def is_configured(self) -> bool:
        return bool(self.api_key)
