from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage

from graph.history import bounded_messages
from graph.limits import KEEP_RECENT
from graph.nodes import call_llm
from graph.prompts import STATIC_SYSTEM_PREFIX, build_llm_messages
from tests.helpers import FakeChat, sample_turn_state


def _fifty_messages():
    messages = []
    for index in range(50):
        if index % 2 == 0:
            messages.append(HumanMessage(content=f"user-{index}"))
        else:
            messages.append(AIMessage(content=f"char-{index}"))
    return messages


def test_bounded_history_excludes_old_messages():
    messages = _fifty_messages()
    window = bounded_messages(messages, keep=KEEP_RECENT)
    assert len(window) == KEEP_RECENT
    assert messages[-1] in window
    assert messages[0] not in window
    for old in messages[:-KEEP_RECENT]:
        assert old not in window


def test_call_llm_does_not_send_all_historical_messages(monkeypatch):
    messages = _fifty_messages()
    fake = FakeChat(AIMessage(content="A nod."))
    monkeypatch.setattr("graph.nodes.chat_model", lambda **kwargs: fake)

    call_llm(sample_turn_state(messages=messages))

    assert len(fake.invocations) == 1
    sent = fake.invocations[0]
    conversational = [msg for msg in sent if not isinstance(msg, SystemMessage)]
    assert len(conversational) == KEEP_RECENT
    assert messages[0] not in conversational
    assert messages[-1] in conversational
    combined = "\n".join(msg.content for msg in sent if isinstance(msg, SystemMessage))
    assert "WORLD_CTX" in combined
    assert "SUM_CTX" in combined
    assert "MEM_CTX" in combined
    assert "user-0" not in "".join(
        msg.content if isinstance(msg.content, str) else "" for msg in conversational
    )


def test_tool_cycle_is_kept_with_recent_window():
    messages = _fifty_messages()
    tool_ai = AIMessage(
        content="",
        tool_calls=[{"name": "remember_event", "args": {"fact": "a vow"}, "id": "call_1"}],
    )
    tool_msg = ToolMessage(content="MEMORY:a vow", tool_call_id="call_1", name="remember_event")
    window = bounded_messages([*messages, tool_ai, tool_msg], keep=KEEP_RECENT)
    assert tool_msg in window
    assert tool_ai in window
    spoken = [msg for msg in window if msg not in (tool_ai, tool_msg)]
    assert len(spoken) == KEEP_RECENT


def test_static_system_prefix_is_stable_across_turns():
    first = build_llm_messages(sample_turn_state(character_id="Aurora", conversation_summary="one"))
    second = build_llm_messages(
        sample_turn_state(character_id="Laxus", conversation_summary="two", reply_length="short")
    )
    assert first[0].content == second[0].content == STATIC_SYSTEM_PREFIX
    assert first[1].content != second[1].content
    assert "conv-test" not in first[0].content
    assert "Aurora" not in first[0].content
