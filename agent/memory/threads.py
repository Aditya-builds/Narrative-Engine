"""UI chat threads stored as JSON files. Survives uvicorn reloads."""

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

from urllib.parse import quote

from config import CHATS_DIR

CHATS_DIR.mkdir(exist_ok=True)


def list_chat_threads() -> list[dict]:
    previews: list[dict] = []
    if not CHATS_DIR.exists():
        return previews
    for path in CHATS_DIR.glob("*.json"):
        thread = _read(path)
        if not thread:
            continue
        preview = _preview(thread)
        if preview:
            previews.append(preview)
    previews.sort(key=lambda item: item.get("at") or "", reverse=True)
    return previews


def load_chat_thread(character: str) -> dict | None:
    return _read(_path(character))


def save_chat_thread(character: str, thread: dict) -> dict:
    payload = {
        "conversation_id": str(thread.get("conversation_id") or ""),
        "character": character,
        "persona_name": str(thread.get("persona_name") or ""),
        "reply_length": _reply_length(thread.get("reply_length")),
        "updated_at": thread.get("updated_at") or datetime.now(timezone.utc).isoformat(),
        "messages": [_message(item) for item in thread.get("messages") or []],
    }
    path = _path(character)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    tmp.replace(path)
    return payload


def delete_chat_thread(character: str) -> None:
    path = _path(character)
    if path.exists():
        path.unlink()


def _preview(thread: dict) -> dict | None:
    messages = thread.get("messages") or []
    if not any(item.get("speaker") == "persona" for item in messages):
        return None
    last = messages[-1]
    return {
        "character": thread.get("character") or "",
        "persona_name": thread.get("persona_name") or "",
        "conversation_id": thread.get("conversation_id") or "",
        "preview": str(last.get("text") or "").strip() or "Open this chat",
        "at": last.get("at") or thread.get("updated_at") or "",
    }


def _read(path: Path) -> dict | None:
    if not path.exists():
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    if not isinstance(data, dict):
        return None
    return data


def _message(item: dict) -> dict:
    speaker = item.get("speaker")
    return {
        "speaker": speaker if speaker in {"character", "persona"} else "character",
        "name": str(item.get("name") or ""),
        "text": str(item.get("text") or ""),
        "at": str(item.get("at") or datetime.now(timezone.utc).isoformat()),
    }


def _reply_length(value: object) -> str:
    if value in {"short", "medium", "long"}:
        return str(value)
    return "medium"


def _path(character: str) -> Path:
    raw = character.strip()
    if not raw:
        raise ValueError("character is empty")
    safe = quote(raw, safe="")
    if not safe:
        raise ValueError("character is empty")
    return CHATS_DIR / f"{safe}.json"
