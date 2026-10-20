import html
from app.schemas import AlertmanagerWebhook


def escape_html(text: str | None) -> str:
    if not text:
        return ""
    return html.escape(str(text))


def format_alert_message(payload: AlertmanagerWebhook) -> str:
    group_status = payload.status.lower()
    alerts = payload.alerts

    if not alerts:
        alertname = escape_html(payload.commonLabels.get("alertname", "unknown_alert"))
        summary = escape_html(payload.commonAnnotations.get("summary", "no summary provided"))
        return f"<b>[{group_status}] {alertname}</b>\n{summary}"

    messages: list[str] = []
    for alert in alerts:
        alert_status = (alert.status or group_status).lower()
        labels = alert.labels
        annotations = alert.annotations

        alertname = escape_html(labels.get("alertname", "unknown_alert"))
        severity = escape_html(labels.get("severity", "info").lower())
        app_name = escape_html(labels.get("app", labels.get("job", "unknown")))
        namespace = escape_html(labels.get("namespace", "delivery"))

        summary = escape_html(annotations.get("summary", ""))
        description = escape_html(annotations.get("description", ""))

        status_header = f"<b>[{alert_status}] {alertname}</b>"

        item_lines = [
            status_header,
            f"• <b>severity:</b> {severity}",
            f"• <b>service:</b> <code>{app_name}</code>",
            f"• <b>namespace:</b> <code>{namespace}</code>",
        ]

        if summary:
            item_lines.append(f"• <b>summary:</b> {summary}")
        if description:
            item_lines.append(f"• <b>description:</b> {description}")

        if alert.startsAt and alert_status == "firing":
            formatted_time = alert.startsAt[:19].replace("T", " ")
            item_lines.append(f"• <b>started:</b> <code>{escape_html(formatted_time)} utc</code>")
        elif alert.endsAt and alert_status == "resolved":
            formatted_time = alert.endsAt[:19].replace("T", " ")
            item_lines.append(f"• <b>ended:</b> <code>{escape_html(formatted_time)} utc</code>")

        messages.append("\n".join(item_lines))

    return "\n\n— — — — — — — — — —\n\n".join(messages)
