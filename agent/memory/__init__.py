import json

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, ToolMessage

from config import CONVERSATIONS_DIR

CONVERSATIONS_DIR.mkdir(exist_ok=True)


def load_conversation(conversation_id: str) -> dict:
    path = _path(conversation_id)
    if not path.exists():
        return {
            "conversation_id": conversation_id,
            "conversation_summary": "",
            "important_memories": [],
            "messages": [],
        }
    return json.loads(path.read_text(encoding="utf-8"))


def save_conversation(state: dict) -> None:
    conversation_id = state["conversation_id"]
    payload = {
        "conversation_id": conversation_id,
        "character_id": state.get("character_id", ""),
        "persona_id": state.get("persona_id", ""),
        "conversation_summary": state.get("conversation_summary") or "",
        "important_memories": state.get("important_memories") or [],
        "messages": [_message_to_json(msg) for msg in _spoken_history(state.get("messages") or [])],
    }
    _path(conversation_id).write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def delete_conversation(conversation_id: str) -> None:
    path = _path(conversation_id)
    if path.exists():
        path.unlink()


def messages_from_json(raw: list) -> list[BaseMessage]:
    restored: list[BaseMessage] = []
    for item in raw:
        kind = item.get("type")
        content = item.get("content") or ""
        if kind == "human":
            restored.append(HumanMessage(content=content))
        elif kind == "ai":
            restored.append(AIMessage(content=content))
        elif kind == "tool":
            restored.append(
                ToolMessage(
                    content=content,
                    tool_call_id=item.get("tool_call_id") or "memory",
                    name=item.get("name") or "memory",
                )
            )
    return restored


def _spoken_history(messages: list) -> list:
    spoken = [
        msg
        for msg in messages
        if isinstance(msg, HumanMessage)
        or (isinstance(msg, AIMessage) and not getattr(msg, "tool_calls", None))
    ]
    return spoken[-16:]


def _message_to_json(msg: BaseMessage) -> dict:
    if isinstance(msg, HumanMessage):
        return {"type": "human", "content": msg.content}
    if isinstance(msg, AIMessage):
        return {"type": "ai", "content": msg.content}
    if isinstance(msg, ToolMessage):
        return {
            "type": "tool",
            "content": msg.content,
            "tool_call_id": msg.tool_call_id,
            "name": msg.name,
        }
    return {"type": "human", "content": str(msg.content)}


def _path(conversation_id: str):
    safe = "".join(ch for ch in conversation_id if ch.isalnum() or ch in "-_")
    if not safe:
        raise ValueError("conversation_id is empty")
    return CONVERSATIONS_DIR / f"{safe}.json"
