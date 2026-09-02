from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

ROOT = Path(__file__).resolve().parent


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=ROOT / ".env", extra="ignore")

    openai_model: str = "gpt-4"
    openai_reasoning_effort: str = "none"
    quarkus_base_url: str = "http://localhost:8080"
    enable_mock_llm: bool = False
    chat_rate_limit_per_minute: int = 20
    narrative_json_logs: bool = False


@lru_cache
def get_settings() -> Settings:
    return Settings()
