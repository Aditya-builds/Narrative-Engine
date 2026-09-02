from __future__ import annotations

import threading
import time

_LOCK = threading.Lock()
_CHAT_REQUESTS = 0
_CHAT_SECONDS = 0.0
_TOKEN_TOTAL = 0
_LLM_CALLS = 0


def record_chat(duration_s: float) -> None:
    global _CHAT_REQUESTS, _CHAT_SECONDS
    with _LOCK:
        _CHAT_REQUESTS += 1
        _CHAT_SECONDS += max(duration_s, 0.0)


def record_tokens(total: int | None) -> None:
    global _TOKEN_TOTAL, _LLM_CALLS
    with _LOCK:
        _LLM_CALLS += 1
        if total:
            _TOKEN_TOTAL += int(total)


def prometheus_text() -> str:
    with _LOCK:
        chats = _CHAT_REQUESTS
        seconds = _CHAT_SECONDS
        tokens = _TOKEN_TOTAL
        calls = _LLM_CALLS
    lines = [
        "# HELP narrative_chat_requests_total Chat POST requests",
        "# TYPE narrative_chat_requests_total counter",
        f"narrative_chat_requests_total {chats}",
        "# HELP narrative_chat_duration_seconds_total Time spent in graph.invoke for chat",
        "# TYPE narrative_chat_duration_seconds_total counter",
        f"narrative_chat_duration_seconds_total {seconds:.6f}",
        "# HELP narrative_llm_tokens_total Token usage recorded from model calls",
        "# TYPE narrative_llm_tokens_total counter",
        f"narrative_llm_tokens_total {tokens}",
        "# HELP narrative_llm_calls_total LLM invocations recorded",
        "# TYPE narrative_llm_calls_total counter",
        f"narrative_llm_calls_total {calls}",
        f"# generated {int(time.time())}",
        "",
    ]
    return "\n".join(lines)
