import os
from typing import Any

from langchain_openai import ChatOpenAI

DEFAULT_MODEL = "gpt-4"


def configured_model_name() -> str:
    return os.getenv("OPENAI_MODEL", DEFAULT_MODEL)


def chat_model(*, max_tokens: int | None = None) -> ChatOpenAI:
    name = configured_model_name()
    kwargs: dict[str, Any] = {
        "model": name,
        "timeout": 90,
        "max_retries": 1,
    }
    if max_tokens is not None:
        kwargs["max_tokens"] = max_tokens
    if name.lower().startswith("gpt-5"):
        # Chat Completions cannot mix Luna reasoning with function tools.
        kwargs["reasoning_effort"] = os.getenv("OPENAI_REASONING_EFFORT", "none")
        kwargs["temperature"] = 0.7
    else:
        kwargs["temperature"] = 0.7
    return ChatOpenAI(**kwargs)
