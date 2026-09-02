from typing import Literal

from pydantic import BaseModel, Field

ReplyLength = Literal["short", "medium", "long"]


class ChatRequest(BaseModel):
    message: str
    character: str
    persona: str
    conversation_id: str | None = None
    reply_length: ReplyLength = "medium"


class ChatResponse(BaseModel):
    response: str
    conversation_id: str
    applied_state_changes: list[str] = Field(default_factory=list)
