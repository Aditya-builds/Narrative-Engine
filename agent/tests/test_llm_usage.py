from langchain_core.messages import AIMessage, HumanMessage

from graph.nodes import call_llm
from graph.prompts import REPLY_TOKENS
from llm_usage import estimate_cost_usd, extract_usage, last_recorded_usage
from tests.helpers import FakeChat, sample_turn_state


def test_reply_length_maps_to_output_token_limit(monkeypatch):
    captured = {}

    def factory(*, max_tokens=None):
        captured["max_tokens"] = max_tokens
        return FakeChat(AIMessage(content="Short beat."), max_tokens=max_tokens)

    monkeypatch.setattr("graph.nodes.chat_model", factory)
    call_llm(sample_turn_state(reply_length="short"))
    assert captured["max_tokens"] == REPLY_TOKENS["short"] == 250

    call_llm(sample_turn_state(reply_length="medium"))
    assert captured["max_tokens"] == REPLY_TOKENS["medium"] == 600

    call_llm(sample_turn_state(reply_length="long"))
    assert captured["max_tokens"] == REPLY_TOKENS["long"] == 1200


def test_usage_metadata_is_captured(monkeypatch):
    reply = AIMessage(
        content="A glance.",
        usage_metadata={
            "input_tokens": 1800,
            "output_tokens": 320,
            "total_tokens": 2120,
            "input_token_details": {"cache_read": 1200},
            "output_token_details": {"reasoning": 0},
        },
        response_metadata={"model_name": "gpt-test"},
    )
    fake = FakeChat(reply)
    monkeypatch.setattr("graph.nodes.chat_model", lambda **kwargs: fake)

    call_llm(sample_turn_state())
    usage = last_recorded_usage()
    assert usage is not None
    assert usage.node == "call_llm"
    assert usage.model == "gpt-test"
    assert usage.input_tokens == 1800
    assert usage.cached_input_tokens == 1200
    assert usage.output_tokens == 320
    assert usage.reasoning_tokens == 0
    assert usage.total_tokens == 2120
    assert usage.calls_this_turn == 1
    assert usage.latency_ms is not None


def test_zero_token_counts_are_preserved():
    reply = AIMessage(
        content="ok",
        usage_metadata={
            "input_tokens": 0,
            "output_tokens": 0,
            "total_tokens": 0,
            "input_token_details": {"cache_read": 0},
            "output_token_details": {"reasoning": 0},
        },
    )
    usage = extract_usage(reply)
    assert usage["input_tokens"] == 0
    assert usage["output_tokens"] == 0
    assert usage["cached_input_tokens"] == 0
    assert usage["reasoning_tokens"] == 0
    assert usage["total_tokens"] == 0


def test_openai_style_usage_metadata_is_extracted():
    reply = AIMessage(
        content="ok",
        response_metadata={
            "model_name": "gpt-4",
            "token_usage": {
                "prompt_tokens": 100,
                "completion_tokens": 20,
                "total_tokens": 120,
                "prompt_tokens_details": {"cached_tokens": 40},
                "completion_tokens_details": {"reasoning_tokens": 5},
            },
        },
    )
    usage = extract_usage(reply)
    assert usage["input_tokens"] == 100
    assert usage["output_tokens"] == 20
    assert usage["cached_input_tokens"] == 40
    assert usage["reasoning_tokens"] == 5
    assert usage["total_tokens"] == 120


def test_missing_usage_metadata_does_not_crash(monkeypatch):
    fake = FakeChat(AIMessage(content="Silence, then a nod."))
    monkeypatch.setattr("graph.nodes.chat_model", lambda **kwargs: fake)

    result = call_llm(sample_turn_state(messages=[HumanMessage(content="Hi")]))
    assert result["messages"][0].content.startswith("Silence")
    usage = last_recorded_usage()
    assert usage is not None
    assert usage.input_tokens is None
    assert usage.output_tokens is None
    assert usage.total_tokens is None
    assert usage.estimated_cost_usd is None


def test_cost_estimate_uses_configured_pricing(monkeypatch):
    monkeypatch.setenv(
        "LLM_MODEL_PRICING_JSON",
        '{"gpt-test": {"input": 1.0, "cached_input": 0.5, "output": 2.0}}',
    )
    cost = estimate_cost_usd(
        "gpt-test",
        {
            "input_tokens": 1_000_000,
            "cached_input_tokens": 200_000,
            "output_tokens": 1_000_000,
        },
    )
    # 800k uncached * $1/m + 200k cached * $0.5/m + 1m output * $2/m
    assert cost == 0.8 + 0.1 + 2.0


def test_cost_estimate_unavailable_without_pricing(monkeypatch):
    monkeypatch.delenv("LLM_MODEL_PRICING_JSON", raising=False)
    monkeypatch.delenv("LLM_PRICE_INPUT_PER_MILLION", raising=False)
    monkeypatch.delenv("LLM_PRICE_CACHED_INPUT_PER_MILLION", raising=False)
    monkeypatch.delenv("LLM_PRICE_OUTPUT_PER_MILLION", raising=False)
    assert estimate_cost_usd("unknown-model", {"input_tokens": 10, "output_tokens": 5}) is None


def test_usage_is_appended_to_a_persistent_file(_usage_log_path):
    import json

    from llm_usage import LLMUsage, record_usage

    first = LLMUsage(
        conversation_id="conv-a",
        node="call_llm",
        model="gpt-test",
        input_tokens=10,
        output_tokens=4,
        total_tokens=14,
        latency_ms=12.5,
        calls_this_turn=1,
    )
    second = LLMUsage(
        conversation_id="conv-b",
        node="summarize",
        model="gpt-test",
        input_tokens=20,
        output_tokens=6,
        total_tokens=26,
        latency_ms=8.0,
        calls_this_turn=0,
    )
    record_usage(first)
    record_usage(second)

    lines = _usage_log_path.read_text(encoding="utf-8").strip().splitlines()
    assert len(lines) == 2
    rows = [json.loads(line) for line in lines]
    assert rows[0]["conversation_id"] == "conv-a"
    assert rows[0]["input_tokens"] == 10
    assert rows[0]["total_tokens"] == 14
    assert rows[0]["recorded_at"]
    assert rows[1]["conversation_id"] == "conv-b"
    assert rows[1]["node"] == "summarize"
    assert "api_key" not in rows[0]
    assert "prompt" not in rows[0]
