# Test helpers live in tests/helpers.py so pytest can collect without importing this module.

import pytest


@pytest.fixture(autouse=True)
def _usage_log_path(tmp_path, monkeypatch):
    path = tmp_path / "llm_usage.jsonl"
    monkeypatch.setattr("llm_usage.USAGE_LOG_PATH", path)
    monkeypatch.delenv("ENABLE_MOCK_LLM", raising=False)
    from settings import get_settings

    get_settings.cache_clear()
    yield path
    get_settings.cache_clear()
