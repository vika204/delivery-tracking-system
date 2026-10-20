from fastapi import APIRouter, Response, status

from app.config import settings

router = APIRouter(tags=["Monitoring"])


@router.get("/health")
@router.get("/actuator/health")
async def liveness():
    return {"status": "up", "service": "telegram-alert-bridge"}


@router.get("/ready")
async def readiness(response: Response):
    missing = [
        name
        for name, value in (("BOT_TOKEN", settings.bot_token), ("CHAT_ID", settings.chat_id))
        if not value
    ]

    if missing:
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
        return {"status": "not_ready", "missing": missing}

    return {"status": "ready", "service": "telegram-alert-bridge"}