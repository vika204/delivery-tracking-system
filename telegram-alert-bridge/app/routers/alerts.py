import logging
from fastapi import APIRouter, HTTPException, status
from app.schemas import AlertmanagerWebhook
from app.services.formatter import format_alert_message
from app.services.telegram import telegram_service, TelegramDeliveryError

logger = logging.getLogger("telegram-alert-bridge.alerts")
router = APIRouter(tags=["Alerting"])


@router.post("/alert", status_code=status.HTTP_200_OK)
async def handle_alert(payload: AlertmanagerWebhook):
    formatted_message = format_alert_message(payload)
    logger.info("Formatted Alertmanager notification (%d alerts):\n%s", len(payload.alerts), formatted_message)

    try:
        await telegram_service.send_message(formatted_message)
        return {"status": "ok", "delivered": True}
    except TelegramDeliveryError as exc:
        logger.error("Failed to forward alert to Telegram: %s", exc)

        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"Telegram delivery failed: {exc.message}"
        )
