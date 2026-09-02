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


class ChatThreadMessage(BaseModel):
    speaker: Literal["character", "persona"]
    name: str = ""
    text: str = ""
    at: str = ""


class ChatThread(BaseModel):
    conversation_id: str = ""
    character: str = ""
    persona_name: str = ""
    reply_length: ReplyLength = "medium"
    updated_at: str | None = None
    messages: list[ChatThreadMessage] = Field(default_factory=list)


class ChatThreadPreview(BaseModel):
    character: str
    persona_name: str
    conversation_id: str = ""
    preview: str = ""
    at: str = ""
