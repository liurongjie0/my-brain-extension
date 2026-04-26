from contextlib import asynccontextmanager

from fastapi import FastAPI, Request, Query
from fastapi.responses import PlainTextResponse

from app.wechat.callback import WeChatCallbackHandler


callback_handler = WeChatCallbackHandler()


@asynccontextmanager
async def lifespan(app: FastAPI):
    yield


app = FastAPI(title="Hiking Bot", lifespan=lifespan)


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.get("/wechat/callback")
async def wechat_verify(
    msg_signature: str = Query(...),
    timestamp: str = Query(...),
    nonce: str = Query(...),
    echostr: str = Query(...),
):
    """企业微信回调 URL 验证"""
    result = callback_handler.verify_url(msg_signature, timestamp, nonce, echostr)
    return PlainTextResponse(content=result)


@app.post("/wechat/callback")
async def wechat_callback(request: Request):
    """接收企业微信消息推送"""
    body = await request.body()
    reply = await callback_handler.handle_message(body)
    return PlainTextResponse(content=reply, media_type="application/xml")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
