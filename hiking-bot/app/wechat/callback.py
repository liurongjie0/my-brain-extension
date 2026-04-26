import hashlib
import xml.etree.ElementTree as ET

from fastapi import Request

from app.config import Config
from app.conversation.manager import ConversationManager
from app.wechat.message import WeChatMessageSender


class WeChatCallbackHandler:
    def __init__(self):
        self.token = Config.WECHAT_TOKEN
        self.manager = ConversationManager()
        self.sender = WeChatMessageSender()

    def verify_url(self, signature: str, timestamp: str, nonce: str, echostr: str) -> str:
        """验证企业微信回调 URL"""
        tmp_list = [self.token, timestamp, nonce]
        tmp_list.sort()
        tmp_str = "".join(tmp_list)
        hashcode = hashlib.sha1(tmp_str.encode()).hexdigest()

        if hashcode == signature:
            return echostr
        return ""

    async def handle_message(self, body: bytes) -> str:
        """处理企业微信推送的消息"""
        try:
            root = ET.fromstring(body)
            msg_type = root.find("MsgType")
            if msg_type is None:
                return "success"

            msg_type_text = msg_type.text

            # 只处理文本消息
            if msg_type_text != "text":
                return "success"

            from_user = root.find("FromUserName")
            to_user = root.find("ToUserName")
            content = root.find("Content")

            if from_user is None or content is None:
                return "success"

            user_id = from_user.text or ""
            message = content.text or ""
            # 企业微信中，群聊消息的 FromUserName 是发送者，可通过 AgentID 等区分群
            # 这里简化处理，用 user_id 作为 group_id（实际场景中需解析 RoomID 或 ChatInfo）
            # TODO: 接入群机器人后，需要解析 ChatId 获取真正的群 ID
            group_id = user_id  # 临时简化

            # 调用对话管理器处理
            reply = await self.manager.handle(user_id, group_id, message)

            # 返回 XML 响应（被动回复）
            return self._build_xml_response(to_user.text or "", user_id, reply)

        except Exception:
            return "success"

    def _build_xml_response(self, to_user: str, from_user: str, content: str) -> str:
        return f"""<xml>
<ToUserName><![CDATA[{to_user}]]></ToUserName>
<FromUserName><![CDATA[{from_user}]]></FromUserName>
<CreateTime>0</CreateTime>
<MsgType><![CDATA[text]]></MsgType>
<Content><![CDATA[{content}]]></Content>
</xml>"""

    async def handle_event(self, body: bytes) -> str:
        """处理企业微信事件推送（如加入群聊等）"""
        return "success"
