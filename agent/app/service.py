import logging
import time
import uuid

from fastapi import BackgroundTasks, HTTPException
from langchain_core.messages import HumanMessage

from app.errors import http_error_from_exception
from app.schemas import ChatRequest, ChatResponse
from graph.model import mock_llm_enabled, reset_api_key, resolved_api_key, use_api_key
from graph.summary import pending_summary_messages, run_summary_maintenance
from memory import load_conversation, messages_from_json, save_conversation
from metrics import record_chat

logger = logging.getLogger(__name__)

MISSING_API_KEY_DETAIL = (
    "No OpenAI API key is available. Paste your own key, or add OPENAI_API_KEY to the agent .env."
)


def run_chat(
    graph,
    request: ChatRequest,
    background_tasks: BackgroundTasks | None = None,
    openai_api_key: str | None = None,
) -> ChatResponse:
    message = request.message.strip()
    character = request.character.strip()
    persona = request.persona.strip()
    if not message:
        raise HTTPException(status_code=400, detail="Write a message first.")
    if not character or not persona:
        raise HTTPException(status_code=400, detail="Choose a character and persona first.")

    token = use_api_key(openai_api_key)
    try:
        api_key = resolved_api_key()
        if not api_key and not mock_llm_enabled():
            raise HTTPException(status_code=401, detail=MISSING_API_KEY_DETAIL)

        thread_id = request.conversation_id or str(uuid.uuid4())
        try:
            stored = load_conversation(thread_id)
            history = messages_from_json(stored.get("messages") or [])
        except Exception as exc:
            raise HTTPException(status_code=503, detail="Could not load this chat's memory.") from exc

        try:
            started = time.perf_counter()
            result = graph.invoke(
                {
                    "conversation_id": thread_id,
                    "character_id": character,
                    "persona_id": persona,
                    "user_message": message,
                    "reply_length": request.reply_length,
                    "conversation_summary": stored.get("conversation_summary") or "",
                    "important_memories": stored.get("important_memories") or [],
                    "messages": [*history, HumanMessage(content=message)],
                    "llm_calls_this_turn": 0,
                }
            )
            record_chat(time.perf_counter() - started)
        except HTTPException:
            raise
        except Exception as exc:
            logger.exception("Chat graph failed")
            raise http_error_from_exception(exc) from exc

        try:
            save_conversation(result)
        except Exception:
            logger.exception("Could not save conversation %s", thread_id)

        if background_tasks is not None and pending_summary_messages(result):
            background_tasks.add_task(run_summary_maintenance, thread_id, api_key)

        return ChatResponse(
            response=(result.get("response") or "").strip() or "…",
            conversation_id=thread_id,
            applied_state_changes=result.get("applied_state_changes") or [],
        )
    finally:
        reset_api_key(token)
