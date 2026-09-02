import json
import os
from typing import Any, Literal

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_openai import ChatOpenAI

from graph.prompts import REPLY_TOKENS, system_prompt
from graph.state import ConversationState
from tools import TOOLS
from world import load_world, select_relevant_context

SUMMARY_AFTER = 16
KEEP_RECENT = 8
DEFAULT_MODEL = "gpt-4"


def load_context(state: ConversationState) -> dict[str, Any]:
    character, persona, notes = load_world(state["character_id"], state["persona_id"])
    return {
        "character_context": character,
        "persona_context": persona,
        "applied_state_changes": notes,
    }


def select_context(state: ConversationState) -> dict[str, Any]:
    return {
        "relevant_context": select_relevant_context(
            state.get("character_context") or {},
            state.get("persona_context") or {},
            state.get("user_message") or "",
        )
    }


def call_llm(state: ConversationState) -> dict[str, Any]:
    length = state.get("reply_length") or "medium"
    model = _chat_model(max_tokens=REPLY_TOKENS.get(length, REPLY_TOKENS["medium"])).bind_tools(TOOLS)
    reply = model.invoke([SystemMessage(content=system_prompt(state)), *state["messages"]])
    return {"messages": [reply]}


def route_llm(state: ConversationState) -> Literal["tools", "update_memory"]:
    last = state["messages"][-1]
    if isinstance(last, AIMessage) and last.tool_calls:
        return "tools"
    return "update_memory"


def update_memory(state: ConversationState) -> dict[str, Any]:
    response = _spoken_reply(state["messages"])
    changes = [
        msg.content
        for msg in state["messages"]
        if isinstance(msg, ToolMessage) and str(msg.content).startswith("CHANGE:")
    ]
    memories = list(state.get("important_memories") or [])
    for msg in state["messages"]:
        if isinstance(msg, ToolMessage) and str(msg.content).startswith("MEMORY:"):
            fact = str(msg.content).removeprefix("MEMORY:").strip()
            if fact and fact not in memories:
                memories.append(fact)
    summary = state.get("conversation_summary") or ""
    human_ai = [
        msg
        for msg in state["messages"]
        if isinstance(msg, (HumanMessage, AIMessage)) and not getattr(msg, "tool_calls", None)
    ]
    if len(human_ai) > SUMMARY_AFTER:
        summary = _refresh_summary(summary, human_ai[:-KEEP_RECENT])
    return {
        "response": response,
        "applied_state_changes": changes,
        "important_memories": memories[-20:],
        "conversation_summary": summary,
    }


def _spoken_reply(messages: list) -> str:
    for msg in reversed(messages):
        if isinstance(msg, AIMessage) and not msg.tool_calls:
            text = msg.content if isinstance(msg.content, str) else str(msg.content)
            if text.strip():
                return text
    return "..."


def _refresh_summary(previous: str, older: list) -> str:
    lines = []
    for msg in older:
        role = "user" if isinstance(msg, HumanMessage) else "character"
        text = msg.content if isinstance(msg.content, str) else json.dumps(msg.content)
        lines.append(f"{role}: {text}")
    try:
        model = _chat_model()
        result = model.invoke(
            [
                SystemMessage(
                    content="Summarize this roleplay so far in 8 short bullet points. Keep names, promises, injuries, and relationship shifts."
                ),
                HumanMessage(
                    content=f"Previous summary:\n{previous or '(none)'}\n\nOlder turns:\n" + "\n".join(lines[-24:])
                ),
            ]
        )
        return result.content if isinstance(result.content, str) else previous
    except Exception:
        return previous


def _chat_model(*, max_tokens: int | None = None) -> ChatOpenAI:
    name = os.getenv("OPENAI_MODEL", DEFAULT_MODEL)
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
