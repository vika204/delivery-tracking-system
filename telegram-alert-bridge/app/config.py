import os
from pydantic import BaseModel, Field


class Settings(BaseModel):
    bot_token: str = Field(
        default_factory=lambda: os.getenv("BOT_TOKEN", "").strip(),
        description="telegram bot token"
    )
    chat_id: str = Field(
        default_factory=lambda: os.getenv("CHAT_ID", "").strip(),
        description="telegram chat or group id"
    )
    port: int = Field(
        default_factory=lambda: int(os.getenv("PORT", "8080")),
        description="server port"
    )
    telegram_api_url: str = Field(
        default="https://api.telegram.org",
        description="telegram api base url"
    )
    http_timeout_seconds: float = Field(
        default=10,
        description="http timeout in seconds"
    )


settings = Settings()