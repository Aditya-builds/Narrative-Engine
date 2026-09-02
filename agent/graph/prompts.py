from langchain_core.messages import SystemMessage

from graph.history import bounded_messages
from graph.limits import KEEP_RECENT
from graph.state import ConversationState

REPLY_GUIDE = {
    "short": "Keep this reply short: 1 to 3 sentences, or one brief physical beat. No long paragraphs.",
    "medium": "Write a medium reply: a few sentences of dialogue and one or two physical beats.",
    "long": "Write a longer scene beat: dialogue, action, and atmosphere. Stay in character and do not ramble off-plot.",
}

REPLY_TOKENS = {"short": 250, "medium": 600, "long": 1200}

# Stable prefix for prompt-cache friendliness. No timestamps, request IDs, or per-turn values.
STATIC_SYSTEM_INSTRUCTIONS = """You are the named character in a live roleplay. The user speaks as the named persona.
Reply in character as spoken dialogue and short physical beats. Do not mention JSON, APIs, or tools.
World context is already loaded for this turn. Do not request visual-identity.
Do not call get_character or get_persona unless the loaded world context is missing or stale."""

STATIC_RESPONSE_RULES = """You decide WHAT happened. Tools decide HOW numbers change.
If feelings change, call record_relationship_event with an event name and minor/major severity.
If someone is hurt, exhausted, recovers, or trains, call record_stat_event.
Never invent a relationship score or attribute number yourself.
Prefer updating the character's view of the persona when the user insults, praises, or fights."""

STATIC_SYSTEM_PREFIX = f"{STATIC_SYSTEM_INSTRUCTIONS}\n\n{STATIC_RESPONSE_RULES}"


def system_prompt(state: ConversationState) -> str:
    """Full system text (static prefix + dynamic context). Prefer build_llm_messages()."""
    return f"{STATIC_SYSTEM_PREFIX}\n\n{dynamic_context_block(state)}"


def dynamic_context_block(state: ConversationState) -> str:
    character = state.get("character_id") or "the character"
    persona = state.get("persona_id") or "the other person"
    summary = state.get("conversation_summary") or "(none yet)"
    memories = state.get("important_memories") or []
    memory_block = "\n".join(f"- {item}" for item in memories) if memories else "(none yet)"
    length = state.get("reply_length") or "medium"
    length_rule = REPLY_GUIDE.get(length, REPLY_GUIDE["medium"])
    world = state.get("relevant_context") or ""
    return (
        f"You are speaking as {character}. The user is speaking as {persona}.\n"
        f"Reply length: {length_rule}\n\n"
        f"Relevant world context:\n{world}\n\n"
        f"Conversation summary:\n{summary}\n\n"
        f"Important memories:\n{memory_block}"
    )


def build_llm_messages(state: ConversationState, keep: int = KEEP_RECENT) -> list:
    """Cache-friendly messages: static system, dynamic context, recent turns only."""
    return [
        SystemMessage(content=STATIC_SYSTEM_PREFIX),
        SystemMessage(content=dynamic_context_block(state)),
        *bounded_messages(state.get("messages") or [], keep),
    ]
