import logging
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI

from app.config import settings
from app.routers.health import router as health_router

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("telegram-alert-bridge")


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Telegram Alert Bridge starting up on port %d...", settings.port)
    yield
    logger.info("Telegram Alert Bridge shutting down...")


app = FastAPI(
    title="Telegram Alert Bridge",
    description="Asynchronous microservice forwarding Prometheus Alertmanager notifications to Telegram",
    version="1.0.0",
    lifespan=lifespan,
)

app.include_router(health_router)

if __name__ == "__main__":
    uvicorn.run("app.main:app", host="0.0.0.0", port=settings.port, reload=False)