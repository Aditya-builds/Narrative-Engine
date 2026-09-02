import logging
from typing import Any, Literal

from langchain_core.messages import AIMessage, ToolMessage

from graph.limits import BUDGET_EXCEEDED_FALLBACK, MAX_LLM_CALLS_PER_TURN
from graph.model import chat_model
from graph.prompts import REPLY_TOKENS, build_llm_messages
from graph.state import ConversationState
from llm_usage import invoke_llm
from tools import TOOLS
from world import load_world, select_relevant_context

logger = logging.getLogger("narrative.llm")


def load_context(state: ConversationState) -> dict[str, Any]:
    character, persona, notes = load_world(state["character_id"], state["persona_id"])
    return {
        "character_context": character,
        "persona_context": persona,
        "applied_state_changes": notes,
        "llm_calls_this_turn": 0,
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
    calls = int(state.get("llm_calls_this_turn") or 0)
    if calls >= MAX_LLM_CALLS_PER_TURN:
        conversation_id = state.get("conversation_id") or ""
        logger.warning(
            "LLM call budget exceeded conversation=%s node=call_llm calls=%s max=%s",
            conversation_id,
            calls,
            MAX_LLM_CALLS_PER_TURN,
        )
        return {
            "messages": [AIMessage(content=BUDGET_EXCEEDED_FALLBACK)],
            "llm_calls_this_turn": calls,
        }

    length = state.get("reply_length") or "medium"
    model = chat_model(max_tokens=REPLY_TOKENS.get(length, REPLY_TOKENS["medium"])).bind_tools(TOOLS)
    calls += 1
    reply = invoke_llm(
        model,
        build_llm_messages(state),
        node="call_llm",
        conversation_id=str(state.get("conversation_id") or ""),
        calls_this_turn=calls,
    )
    return {"messages": [reply], "llm_calls_this_turn": calls}


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
    return {
        "response": response,
        "applied_state_changes": changes,
        "important_memories": memories[-20:],
    }


def _spoken_reply(messages: list) -> str:
    for msg in reversed(messages):
        if isinstance(msg, AIMessage) and not msg.tool_calls:
            text = msg.content if isinstance(msg.content, str) else str(msg.content)
            if text.strip():
                return text
    return "..."
