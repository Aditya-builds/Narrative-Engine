"""Incremental conversation summarization — maintenance, not the reply path."""

from __future__ import annotations

import json
import logging

from langchain_core.messages import HumanMessage, SystemMessage

from graph.history import spoken_messages
from graph.limits import KEEP_RECENT, SUMMARY_AFTER, SUMMARY_BATCH
from graph.model import chat_model
from llm_usage import invoke_llm

logger = logging.getLogger("narrative.llm")

_SUMMARY_INSTRUCTIONS = (
    "Summarize this roleplay so far in 8 short bullet points. "
    "Keep names, promises, injuries, and relationship shifts. "
    "Merge with the previous summary; do not repeat stale detail."
)


def pending_summary_messages(state: dict, keep: int = KEEP_RECENT, after: int = SUMMARY_AFTER) -> list:
    """Older spoken turns not in the recent window, only once the conversation is long enough."""
    spoken = spoken_messages(state.get("messages") or [])
    if len(spoken) <= after:
        return []
    older = spoken[:-keep] if keep > 0 else spoken
    return older[:SUMMARY_BATCH]


def run_summary_maintenance(conversation_id: str) -> None:
    """Load stored history, fold older turns into the summary, persist the result.

    Safe to run after the HTTP response has been sent.
    """
    from memory import apply_summary_update, load_conversation, messages_from_json

    stored = load_conversation(conversation_id)
    snapshot = {
        "conversation_id": conversation_id,
        "conversation_summary": stored.get("conversation_summary") or "",
        "messages": messages_from_json(stored.get("messages") or []),
    }
    if not pending_summary_messages(snapshot):
        return
    updated = refresh_summary_incremental(snapshot)
    if not updated:
        return
    apply_summary_update(
        conversation_id,
        updated["conversation_summary"],
        updated["summarized_messages"],
    )


def refresh_summary_incremental(state: dict) -> dict | None:
    """Fold a batch of older turns into conversation_summary.

    Returns None when there is nothing new to summarize. Never intended to run
    inside the user-facing graph path; call after the reply is produced.
    """
    older = pending_summary_messages(state)
    if not older:
        return None

    previous = state.get("conversation_summary") or ""
    lines = []
    for msg in older:
        role = "user" if isinstance(msg, HumanMessage) else "character"
        text = msg.content if isinstance(msg.content, str) else json.dumps(msg.content)
        lines.append(f"{role}: {text}")
    try:
        result = invoke_llm(
            chat_model(max_tokens=400),
            [
                SystemMessage(content=_SUMMARY_INSTRUCTIONS),
                HumanMessage(
                    content=f"Previous summary:\n{previous or '(none)'}\n\nNew older turns:\n"
                    + "\n".join(lines)
                ),
            ],
            node="summarize",
            conversation_id=str(state.get("conversation_id") or ""),
            calls_this_turn=0,
        )
        text = result.content if isinstance(getattr(result, "content", None), str) else previous
        if not str(text).strip():
            return None
        return {
            "conversation_summary": text,
            "summarized_messages": older,
        }
    except Exception:
        logger.exception(
            "Incremental summary failed conversation=%s", state.get("conversation_id")
        )
        return None
