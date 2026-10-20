import unittest
from unittest.mock import patch, AsyncMock
from fastapi.testclient import TestClient

from app.main import app
from app.config import settings
from app.services.telegram import TelegramDeliveryError


class TestBridgeAPI(unittest.TestCase):

    def setUp(self):
        self.client = TestClient(app)

    def test_ready_when_configured(self):
        with patch.object(settings, "bot_token", "123:abc"), patch.object(settings, "chat_id", "42"):
            response = self.client.get("/ready")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["status"], "ready")

    def test_not_ready_without_secrets(self):
        with patch.object(settings, "bot_token", ""), patch.object(settings, "chat_id", ""):
            response = self.client.get("/ready")
        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.json()["missing"], ["BOT_TOKEN", "CHAT_ID"])

    def test_health_check(self):
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"status": "up", "service": "telegram-alert-bridge"})

    def test_actuator_health_alias(self):
        response = self.client.get("/actuator/health")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["status"], "up")

    @patch("app.routers.alerts.telegram_service.send_message", new_callable=AsyncMock)
    def test_post_alert_success(self, mock_send):
        mock_send.return_value = {"ok": True, "result": {"message_id": 100}}

        payload = {
            "version": "4",
            "status": "firing",
            "alerts": [
                {
                    "status": "firing",
                    "labels": {
                        "alertname": "ServiceDown",
                        "severity": "critical",
                        "app": "shipment-service",
                        "namespace": "delivery"
                    },
                    "annotations": {
                        "summary": "Service shipment-service is down"
                    }
                }
            ]
        }

        response = self.client.post("/alert", json=payload)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"status": "ok", "delivered": True})
        mock_send.assert_awaited_once()

    @patch("app.routers.alerts.telegram_service.send_message", new_callable=AsyncMock)
    def test_post_alert_upstream_failure(self, mock_send):
        mock_send.side_effect = TelegramDeliveryError(500, "Invalid BOT_TOKEN")

        payload = {
            "version": "4",
            "status": "firing",
            "alerts": [
                {
                    "status": "firing",
                    "labels": {"alertname": "ServiceDown", "app": "shipment-service"}
                }
            ]
        }

        response = self.client.post("/alert", json=payload)
        self.assertEqual(response.status_code, 502)
        self.assertIn("Telegram delivery failed", response.json()["detail"])


if __name__ == "__main__":
    unittest.main()
