from contextvars import ContextVar
import os
from typing import Any

from langchain_openai import ChatOpenAI

DEFAULT_MODEL = "gpt-4"
_PLACEHOLDERS = frozenset({"replace-me", "changeme", "your-key-here", "none", "sk-xxx"})
_REQUEST_API_KEY: ContextVar[str | None] = ContextVar("request_openai_api_key", default=None)


def clean_api_key(value: str | None) -> str | None:
    key = (value or "").strip()
    if not key or key.lower() in _PLACEHOLDERS:
        return None
    return key


def use_api_key(api_key: str | None):
    return _REQUEST_API_KEY.set(clean_api_key(api_key))


def reset_api_key(token) -> None:
    _REQUEST_API_KEY.reset(token)


def resolved_api_key() -> str | None:
    return clean_api_key(_REQUEST_API_KEY.get()) or clean_api_key(os.getenv("OPENAI_API_KEY"))


def configured_model_name() -> str:
    return os.getenv("OPENAI_MODEL", DEFAULT_MODEL)


def chat_model(*, max_tokens: int | None = None) -> ChatOpenAI:
    name = configured_model_name()
    kwargs: dict[str, Any] = {
        "model": name,
        "timeout": 90,
        "max_retries": 1,
    }
    api_key = resolved_api_key()
    if api_key:
        kwargs["api_key"] = api_key
    if max_tokens is not None:
        kwargs["max_tokens"] = max_tokens
    if name.lower().startswith("gpt-5"):
        # Chat Completions cannot mix Luna reasoning with function tools.
        kwargs["reasoning_effort"] = os.getenv("OPENAI_REASONING_EFFORT", "none")
        kwargs["temperature"] = 0.7
    else:
        kwargs["temperature"] = 0.7
    return ChatOpenAI(**kwargs)
