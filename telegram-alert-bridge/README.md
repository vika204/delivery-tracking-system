# telegram-alert-bridge

Сервіс приймає вебхуки від Prometheus Alertmanager (`POST /alert`), перетворює їх у текст та відправляє у Telegram через Bot API.

## Змінні середовища

| Змінна | Опис | За замовчуванням |
|---|---|---|
| `BOT_TOKEN` | Токен Telegram-бота | — |
| `CHAT_ID` | ID чату або групи (рядок) | — |
| `PORT` | Порт HTTP сервера | `8080` |

## Ендпоінти

- `GET /health` — liveness проба
- `GET /ready` — readiness проба (перевіряє наявність токена та chat_id)
- `POST /alert` — приймає Alertmanager v4 JSON
- `GET /docs` — Swagger UI

## Локальний запуск

```bash
cd telegram-alert-bridge
pip install -r requirements.txt

export BOT_TOKEN="your_bot_token"
export CHAT_ID="your_chat_id"
python -m app.main
```

## Тести

```bash
python -m unittest discover tests
```
