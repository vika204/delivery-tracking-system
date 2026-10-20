import logging
import httpx
from app.config import settings

logger = logging.getLogger("telegram-alert-bridge.telegram")


class TelegramDeliveryError(Exception):
    def __init__(self, status_code: int, message: str):
        self.status_code = status_code
        self.message = message
        super().__init__(f"Telegram error {status_code}: {message}")


class TelegramService:
    def __init__(self, client: httpx.AsyncClient | None = None):
        self._client = client

    @property
    def client(self) -> httpx.AsyncClient:
        if self._client is None or self._client.is_closed:
            self._client = httpx.AsyncClient(timeout=settings.http_timeout_seconds)
        return self._client

    async def close(self):
        if self._client is not None and not self._client.is_closed:
            await self._client.aclose()

    async def send_message(self, text: str) -> dict:
        if not settings.bot_token or not settings.chat_id:
            raise TelegramDeliveryError(500, "BOT_TOKEN or CHAT_ID environment variables are not configured")

        url = f"{settings.telegram_api_url}/bot{settings.bot_token}/sendMessage"
        payload = {
            "chat_id": settings.chat_id,
            "text": text,
            "parse_mode": "HTML",
            "disable_web_page_preview": True,
        }

        try:
            response = await self.client.post(url, json=payload)
            if response.is_success:
                logger.info("Alert delivered to Telegram chat %s successfully", settings.chat_id)
                return response.json()
            else:
                logger.error("Telegram API rejected message (HTTP %d): %s", response.status_code, response.text)
                raise TelegramDeliveryError(response.status_code, response.text)
        except httpx.RequestError as exc:
            logger.error("Network error while communicating with Telegram: %s", type(exc).__name__)
            raise TelegramDeliveryError(502, f"Network error contacting Telegram: {exc}")


telegram_service = TelegramService()
