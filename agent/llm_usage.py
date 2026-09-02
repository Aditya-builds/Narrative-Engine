"""LLM usage / cost tracking. Safe to call even when providers omit fields."""

from __future__ import annotations

import json
import logging
import os
import time
from dataclasses import dataclass
from typing import Any

logger = logging.getLogger("narrative.llm")

# USD per 1M tokens. Left empty on purpose — fill via env rather than guessed rates.
# Shape:
#   MODEL_PRICING = {
#       "model-name": {"input": ..., "cached_input": ..., "output": ...},
#   }
MODEL_PRICING: dict[str, dict[str, float]] = {}


@dataclass(frozen=True)
class LLMUsage:
    conversation_id: str
    node: str
    model: str
    input_tokens: int | None = None
    cached_input_tokens: int | None = None
    output_tokens: int | None = None
    reasoning_tokens: int | None = None
    total_tokens: int | None = None
    latency_ms: float | None = None
    calls_this_turn: int | None = None
    estimated_cost_usd: float | None = None


_LAST_USAGE: LLMUsage | None = None


def last_recorded_usage() -> LLMUsage | None:
    return _LAST_USAGE


def extract_usage(response: Any) -> dict[str, int | None]:
    """Pull token counts from LangChain / OpenAI metadata when present."""
    usage: dict[str, int | None] = {
        "input_tokens": None,
        "cached_input_tokens": None,
        "output_tokens": None,
        "reasoning_tokens": None,
        "total_tokens": None,
    }
    if response is None:
        return usage

    meta = getattr(response, "usage_metadata", None)
    if isinstance(meta, dict) and meta:
        usage["input_tokens"] = _as_int(meta.get("input_tokens"))
        usage["output_tokens"] = _as_int(meta.get("output_tokens"))
        usage["total_tokens"] = _as_int(meta.get("total_tokens"))
        in_details = meta.get("input_token_details") or meta.get("input_tokens_details") or {}
        if isinstance(in_details, dict):
            usage["cached_input_tokens"] = _first_int(
                in_details.get("cache_read"),
                in_details.get("cached_tokens"),
                in_details.get("cache_read_tokens"),
            )
        out_details = meta.get("output_token_details") or meta.get("output_tokens_details") or {}
        if isinstance(out_details, dict):
            usage["reasoning_tokens"] = _first_int(
                out_details.get("reasoning"),
                out_details.get("reasoning_tokens"),
            )

    rm = getattr(response, "response_metadata", None) or {}
    if isinstance(rm, dict):
        tu = rm.get("token_usage") or rm.get("usage") or {}
        if isinstance(tu, dict) and tu:
            if usage["input_tokens"] is None:
                usage["input_tokens"] = _first_int(tu.get("prompt_tokens"), tu.get("input_tokens"))
            if usage["output_tokens"] is None:
                usage["output_tokens"] = _first_int(tu.get("completion_tokens"), tu.get("output_tokens"))
            if usage["total_tokens"] is None:
                usage["total_tokens"] = _as_int(tu.get("total_tokens"))
            ptd = tu.get("prompt_tokens_details") or {}
            if isinstance(ptd, dict) and usage["cached_input_tokens"] is None:
                usage["cached_input_tokens"] = _first_int(
                    ptd.get("cached_tokens"),
                    ptd.get("cache_read_tokens"),
                    ptd.get("cache_read"),
                )
            ctd = tu.get("completion_tokens_details") or {}
            if isinstance(ctd, dict) and usage["reasoning_tokens"] is None:
                usage["reasoning_tokens"] = _first_int(
                    ctd.get("reasoning_tokens"),
                    ctd.get("reasoning"),
                )

        if usage["input_tokens"] is None:
            usage["input_tokens"] = _first_int(rm.get("prompt_tokens"), rm.get("input_tokens"))
        if usage["output_tokens"] is None:
            usage["output_tokens"] = _first_int(rm.get("completion_tokens"), rm.get("output_tokens"))

    if usage["total_tokens"] is None:
        parts = [usage["input_tokens"], usage["output_tokens"]]
        if all(p is not None for p in parts):
            usage["total_tokens"] = int(parts[0]) + int(parts[1])
    return usage


def model_from_response(response: Any, fallback: str = "unknown") -> str:
    rm = getattr(response, "response_metadata", None) or {}
    if isinstance(rm, dict):
        name = rm.get("model_name") or rm.get("model")
        if name:
            return str(name)
    meta = getattr(response, "usage_metadata", None) or {}
    if isinstance(meta, dict) and meta.get("model"):
        return str(meta["model"])
    return fallback


def get_model_pricing() -> dict[str, dict[str, float]]:
    """Merge in-code MODEL_PRICING with optional environment configuration."""
    pricing = {name: dict(rates) for name, rates in MODEL_PRICING.items()}
    raw = os.getenv("LLM_MODEL_PRICING_JSON")
    if raw:
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            logger.warning("LLM_MODEL_PRICING_JSON is not valid JSON; ignoring")
            parsed = None
        if isinstance(parsed, dict):
            for name, rates in parsed.items():
                if isinstance(rates, dict):
                    cleaned = _clean_rates(rates)
                    if cleaned:
                        pricing[str(name)] = {**pricing.get(str(name), {}), **cleaned}

    model = os.getenv("OPENAI_MODEL")
    env_rates = _clean_rates(
        {
            "input": os.getenv("LLM_PRICE_INPUT_PER_MILLION"),
            "cached_input": os.getenv("LLM_PRICE_CACHED_INPUT_PER_MILLION"),
            "output": os.getenv("LLM_PRICE_OUTPUT_PER_MILLION"),
        }
    )
    if model and env_rates:
        pricing[model] = {**pricing.get(model, {}), **env_rates}
    return pricing


def estimate_cost_usd(model: str, usage: dict[str, int | None]) -> float | None:
    rates = _rates_for_model(model)
    if not rates:
        return None
    input_tokens = usage.get("input_tokens") or 0
    cached = usage.get("cached_input_tokens") or 0
    output_tokens = usage.get("output_tokens") or 0
    if input_tokens is None and output_tokens is None:
        return None
    uncached = max(int(input_tokens) - int(cached), 0)
    cost = 0.0
    known = False
    if "input" in rates:
        cost += uncached * rates["input"] / 1_000_000
        known = True
    if cached and "cached_input" in rates:
        cost += int(cached) * rates["cached_input"] / 1_000_000
        known = True
    elif cached and "input" in rates:
        cost += int(cached) * rates["input"] / 1_000_000
        known = True
    if "output" in rates:
        cost += int(output_tokens) * rates["output"] / 1_000_000
        known = True
    return cost if known else None


def record_usage(usage: LLMUsage) -> LLMUsage:
    global _LAST_USAGE
    _LAST_USAGE = usage
    logger.info(
        "[LLM] model=%s node=%s input=%s cached=%s output=%s reasoning=%s total=%s latency=%s turn_calls=%s cost_usd=%s",
        usage.model,
        usage.node,
        _dash(usage.input_tokens),
        _dash(usage.cached_input_tokens),
        _dash(usage.output_tokens),
        _dash(usage.reasoning_tokens),
        _dash(usage.total_tokens),
        _latency(usage.latency_ms),
        _dash(usage.calls_this_turn),
        _cost(usage.estimated_cost_usd),
    )
    logger.debug(
        "LLM Usage\n"
        "---------\n"
        "conversation: %s\n"
        "node: %s\n"
        "model: %s\n"
        "\n"
        "input_tokens: %s\n"
        "cached_input_tokens: %s\n"
        "output_tokens: %s\n"
        "reasoning_tokens: %s\n"
        "total_tokens: %s\n"
        "\n"
        "latency_ms: %s\n"
        "calls_this_turn: %s",
        usage.conversation_id or "-",
        usage.node,
        usage.model,
        _dash(usage.input_tokens),
        _dash(usage.cached_input_tokens),
        _dash(usage.output_tokens),
        _dash(usage.reasoning_tokens),
        _dash(usage.total_tokens),
        _latency(usage.latency_ms),
        _dash(usage.calls_this_turn),
    )
    return usage


def invoke_llm(
    model: Any,
    messages: list,
    *,
    node: str,
    conversation_id: str = "",
    calls_this_turn: int = 1,
    model_name: str | None = None,
) -> Any:
    """Invoke a chat model and record usage. Never logs prompts or credentials."""
    fallback_name = model_name or _model_attr(model) or "unknown"
    started = time.perf_counter()
    reply = model.invoke(messages)
    latency_ms = (time.perf_counter() - started) * 1000
    token_usage = extract_usage(reply)
    resolved_model = model_from_response(reply, fallback_name)
    record_usage(
        LLMUsage(
            conversation_id=conversation_id or "",
            node=node,
            model=resolved_model,
            input_tokens=token_usage["input_tokens"],
            cached_input_tokens=token_usage["cached_input_tokens"],
            output_tokens=token_usage["output_tokens"],
            reasoning_tokens=token_usage["reasoning_tokens"],
            total_tokens=token_usage["total_tokens"],
            latency_ms=latency_ms,
            calls_this_turn=calls_this_turn,
            estimated_cost_usd=estimate_cost_usd(resolved_model, token_usage),
        )
    )
    return reply


def _rates_for_model(model: str) -> dict[str, float]:
    pricing = get_model_pricing()
    if model in pricing:
        return pricing[model]
    lowered = (model or "").lower()
    for name, rates in pricing.items():
        if name.lower() == lowered or lowered.startswith(name.lower()):
            return rates
    return {}


def _clean_rates(rates: dict) -> dict[str, float]:
    cleaned: dict[str, float] = {}
    for key in ("input", "cached_input", "output"):
        value = rates.get(key)
        if value is None or value == "":
            continue
        try:
            cleaned[key] = float(value)
        except (TypeError, ValueError):
            continue
    return cleaned


def _model_attr(model: Any) -> str | None:
    for attr in ("model_name", "model"):
        value = getattr(model, attr, None)
        if isinstance(value, str) and value:
            return value
    return None


def _as_int(value: Any) -> int | None:
    if value is None or value == "":
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _first_int(*values: Any) -> int | None:
    for value in values:
        parsed = _as_int(value)
        if parsed is not None:
            return parsed
    return None


def _dash(value: int | None) -> str:
    return "-" if value is None else str(value)


def _latency(value: float | None) -> str:
    if value is None:
        return "-"
    return str(int(round(value)))


def _cost(value: float | None) -> str:
    if value is None:
        return "-"
    return f"{value:.6f}"
