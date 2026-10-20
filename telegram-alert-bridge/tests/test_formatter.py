import unittest
from app.schemas import AlertmanagerWebhook, AlertItem
from app.services.formatter import escape_html, format_alert_message


class TestAlertFormatter(unittest.TestCase):

    def test_escape_html(self):
        self.assertEqual(escape_html("Service <down> & 'alert'"), "Service &lt;down&gt; &amp; &#x27;alert&#x27;")
        self.assertEqual(escape_html(""), "")
        self.assertEqual(escape_html(None), "")

    def test_format_firing_alert(self):
        payload = AlertmanagerWebhook(
            version="4",
            status="firing",
            alerts=[
                AlertItem(
                    status="firing",
                    labels={
                        "alertname": "ServiceDown",
                        "severity": "critical",
                        "app": "shipment-service",
                        "namespace": "delivery"
                    },
                    annotations={
                        "summary": "Service shipment-service is down",
                        "description": "Pod shipment-service-xyz unreachable"
                    },
                    startsAt="2026-10-19T14:30:00.000Z"
                )
            ]
        )
        result = format_alert_message(payload)
        self.assertIn("<b>[firing] ServiceDown</b>", result)
        self.assertIn("• <b>severity:</b> critical", result)
        self.assertIn("<code>shipment-service</code>", result)
        self.assertIn("<code>delivery</code>", result)
        self.assertIn("Service shipment-service is down", result)
        self.assertIn("2026-10-19 14:30:00 utc", result)

    def test_format_resolved_alert(self):
        payload = AlertmanagerWebhook(
            version="4",
            status="resolved",
            alerts=[
                AlertItem(
                    status="resolved",
                    labels={
                        "alertname": "HighFailedShipments",
                        "severity": "warning",
                        "app": "shipment-service",
                        "namespace": "delivery"
                    },
                    annotations={
                        "summary": "High shipment failure rate"
                    },
                    startsAt="2026-10-19T14:30:00.000Z",
                    endsAt="2026-10-19T14:35:00.000Z"
                )
            ]
        )
        result = format_alert_message(payload)
        self.assertIn("<b>[resolved] HighFailedShipments</b>", result)
        self.assertIn("• <b>severity:</b> warning", result)
        self.assertIn("High shipment failure rate", result)
        self.assertIn("• <b>ended:</b>", result)
        self.assertIn("2026-10-19 14:35:00 utc", result)

    def test_format_multiple_grouped_alerts(self):
        payload = AlertmanagerWebhook(
            version="4",
            status="firing",
            alerts=[
                AlertItem(
                    status="firing",
                    labels={"alertname": "ServiceDown", "app": "auth-service", "severity": "critical"},
                    annotations={"summary": "Auth service down"}
                ),
                AlertItem(
                    status="firing",
                    labels={"alertname": "ServiceDown", "app": "tracking-service", "severity": "critical"},
                    annotations={"summary": "Tracking service down"}
                )
            ]
        )
        result = format_alert_message(payload)
        self.assertIn("auth-service", result)
        self.assertIn("tracking-service", result)
        self.assertIn("— — — — — — — — — —", result)

    def test_format_empty_alerts_fallback(self):
        payload = AlertmanagerWebhook(
            version="4",
            status="firing",
            commonLabels={"alertname": "ClusterDegraded"},
            commonAnnotations={"summary": "Several nodes degraded"},
            alerts=[]
        )
        result = format_alert_message(payload)
        self.assertIn("<b>[firing] ClusterDegraded</b>", result)
        self.assertIn("Several nodes degraded", result)


if __name__ == "__main__":
    unittest.main()
