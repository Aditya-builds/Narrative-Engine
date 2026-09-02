from typing import Annotated, Any, TypedDict

from langchain_core.messages import AnyMessage
from langgraph.graph.message import add_messages


class ConversationState(TypedDict):
    conversation_id: str
    character_id: str
    persona_id: str
    user_message: str
    reply_length: str
    character_context: dict[str, Any]
    persona_context: dict[str, Any]
    relevant_context: str
    conversation_summary: str
    important_memories: list[str]
    messages: Annotated[list[AnyMessage], add_messages]
    response: str
    applied_state_changes: list[str]
