from langchain_core.messages import AIMessage, HumanMessage, ToolMessage

from graph.limits import KEEP_RECENT


def spoken_messages(messages: list) -> list:
    """Human turns and spoken character replies (no tool-call stubs)."""
    return [
        msg
        for msg in messages
        if isinstance(msg, HumanMessage)
        or (isinstance(msg, AIMessage) and not getattr(msg, "tool_calls", None))
    ]


def bounded_messages(messages: list, keep: int = KEEP_RECENT) -> list:
    """Last `keep` conversational messages, plus any trailing tool cycle.

    Older history stays in LangGraph/disk state; it is not sent to the model.
    A trailing AIMessage(tool_calls) + ToolMessage block is kept intact so the
    Chat Completions API still sees a valid tool round-trip.
    """
    if not messages or keep <= 0:
        return []

    selected: list = []
    conv_count = 0
    for msg in reversed(messages):
        if isinstance(msg, ToolMessage):
            selected.append(msg)
            continue
        if isinstance(msg, (HumanMessage, AIMessage)):
            has_tool_calls = bool(getattr(msg, "tool_calls", None))
            if conv_count >= keep:
                if has_tool_calls and selected and isinstance(selected[-1], ToolMessage):
                    selected.append(msg)
                    continue
                break
            selected.append(msg)
            conv_count += 1
            continue

    selected.reverse()
    while selected and isinstance(selected[0], ToolMessage):
        selected.pop(0)
    return selected
