from graph.state import ConversationState

REPLY_GUIDE = {
    "short": "Keep this reply short: 1 to 3 sentences, or one brief physical beat. No long paragraphs.",
    "medium": "Write a medium reply: a few sentences of dialogue and one or two physical beats.",
    "long": "Write a longer scene beat: dialogue, action, and atmosphere. Stay in character and do not ramble off-plot.",
}

REPLY_TOKENS = {"short": 400, "medium": 900, "long": 1800}


def system_prompt(state: ConversationState) -> str:
    character = state.get("character_id") or "the character"
    persona = state.get("persona_id") or "the other person"
    summary = state.get("conversation_summary") or "(none yet)"
    memories = state.get("important_memories") or []
    memory_block = "\n".join(f"- {item}" for item in memories) if memories else "(none yet)"
    length = state.get("reply_length") or "medium"
    length_rule = REPLY_GUIDE.get(length, REPLY_GUIDE["medium"])
    return (
        f"You are {character} in a live roleplay. The user speaks as {persona}.\n"
        "Reply in character as spoken dialogue and short physical beats. Do not mention JSON, APIs, or tools.\n"
        "World context is already loaded for this turn. Do not request visual-identity.\n"
        f"Reply length: {length_rule}\n\n"
        f"{state.get('relevant_context') or ''}\n\n"
        f"Older conversation summary:\n{summary}\n\n"
        f"Important memories:\n{memory_block}\n\n"
        "You decide WHAT happened. Tools decide HOW numbers change.\n"
        "If feelings change, call record_relationship_event with an event name and minor/major severity.\n"
        "If someone is hurt, exhausted, recovers, or trains, call record_stat_event.\n"
        "Never invent a relationship score or attribute number yourself.\n"
        "Prefer updating the character's view of the persona when the user insults, praises, or fights."
    )
