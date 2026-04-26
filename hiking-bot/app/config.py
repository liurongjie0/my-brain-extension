import os
from pathlib import Path

from dotenv import load_dotenv

# 加载项目根目录的 .env 文件
env_path = Path(__file__).parent.parent / ".env"
load_dotenv(dotenv_path=env_path)


class Config:
    # WeChat Work
    WECHAT_CORP_ID = os.getenv("WECHAT_CORP_ID", "")
    WECHAT_AGENT_ID = os.getenv("WECHAT_AGENT_ID", "")
    WECHAT_SECRET = os.getenv("WECHAT_SECRET", "")
    WECHAT_TOKEN = os.getenv("WECHAT_TOKEN", "")
    WECHAT_ENCODING_AES_KEY = os.getenv("WECHAT_ENCODING_AES_KEY", "")

    # AI
    AI_API_KEY = os.getenv("AI_API_KEY", "")
    AI_BASE_URL = os.getenv("AI_BASE_URL", "https://api.openai.com/v1")
    AI_MODEL = os.getenv("AI_MODEL", "gpt-4o-mini")

    # App
    CONVERSATION_TTL = int(os.getenv("CONVERSATION_TTL", "86400"))
    DATA_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "data")
    DATABASE_PATH = os.path.join(DATA_DIR, "hiking.db")
