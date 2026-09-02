from fastapi import HTTPException

from app.errors import error_message, error_payload, http_error_from_exception
from app.rate_limit import SlidingWindowLimiter
from graph.model import chat_model, mock_llm_enabled
from settings import get_settings


def test_http_error_mapping():
    err = http_error_from_exception(RuntimeError("invalid_api_key"))
    assert err.status_code == 401
    timeout = http_error_from_exception(TimeoutError("deadline exceeded"))
    assert timeout.status_code == 504


def test_error_payload_shape():
    class Dummy:
        url = type("U", (), {"path": "/chat"})()

    body = error_payload(Dummy(), 429, "slow down")
    assert body["errorCode"] == "RATE_LIMITED"
    assert body["detail"] == "slow down"
    assert error_message([{"msg": "field required"}]) == "field required"
    assert error_message(None) == "Request failed"


def test_rate_limiter_blocks_after_limit():
    limiter = SlidingWindowLimiter(2, window_s=60)
    assert limiter.allow("a")
    assert limiter.allow("a")
    assert not limiter.allow("a")
    assert limiter.allow("b")


def test_mock_llm_flag(monkeypatch):
    monkeypatch.delenv("ENABLE_MOCK_LLM", raising=False)
    get_settings.cache_clear()
    assert mock_llm_enabled() is False
    monkeypatch.setenv("ENABLE_MOCK_LLM", "true")
    assert mock_llm_enabled() is True
    model = chat_model(max_tokens=40)
    reply = model.invoke([type("M", (), {"content": "Ping"})()])
    assert "[mock]" in reply.content
