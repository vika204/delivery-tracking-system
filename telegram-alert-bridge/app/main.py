import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
import uvicorn

from app.config import settings
from app.routers.health import router as health_router
from app.routers.alerts import router as alerts_router
from app.services.telegram import telegram_service

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)
logger = logging.getLogger("telegram-alert-bridge")


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Telegram Alert Bridge starting up on port %d...", settings.port)
    yield
    logger.info("Telegram Alert Bridge shutting down, releasing connection pools...")
    await telegram_service.close()


app = FastAPI(
    title="Telegram Alert Bridge",
    description="Asynchronous microservice forwarding Prometheus Alertmanager notifications to Telegram",
    version="1.0.0",
    lifespan=lifespan
)

app.include_router(health_router)
app.include_router(alerts_router)


if __name__ == "__main__":
    uvicorn.run("app.main:app", host="0.0.0.0", port=settings.port, reload=False)
