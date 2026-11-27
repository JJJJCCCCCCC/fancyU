from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="", extra="ignore")

    service_jwt_secret: str = Field(min_length=32)
    java_api_url: str = "http://api:8080"
    java_api_timeout_seconds: float = Field(default=8.0, ge=1, le=30)
    provider_base_url: str | None = None
    provider_api_key: str | None = None
    provider_model: str = "qwen3.5-2b"
    provider_timeout_seconds: float = Field(default=8.0, ge=1, le=30)


@lru_cache
def get_settings() -> Settings:
    return Settings()
