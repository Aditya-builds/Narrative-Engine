from langchain_core.messages import AIMessage, HumanMessage

from graph.limits import KEEP_RECENT, SUMMARY_AFTER, SUMMARY_BATCH
from graph.summary import pending_summary_messages, refresh_summary_incremental
from tests.helpers import FakeChat


def _long_history(count: int) -> list:
    messages = []
    for index in range(count):
        messages.append(HumanMessage(content=f"user-{index}"))
        messages.append(AIMessage(content=f"char-{index}"))
    return messages


def test_summary_not_pending_before_threshold():
    messages = _long_history(SUMMARY_AFTER // 2)
    assert pending_summary_messages({"messages": messages}) == []


def test_summary_is_incremental_and_skips_recent_window():
    messages = _long_history(14)  # 28 spoken turns
    pending = pending_summary_messages({"messages": messages})
    assert pending
    assert len(pending) <= SUMMARY_BATCH
    for recent in messages[-KEEP_RECENT:]:
        assert recent not in pending
    assert messages[0] in pending


def test_refresh_summary_sends_only_pending_older_turns(monkeypatch):
    fake = FakeChat(AIMessage(content="- They met in the guildhall."))
    monkeypatch.setattr("graph.summary.chat_model", lambda **kwargs: fake)

    messages = _long_history(14)
    result = refresh_summary_incremental(
        {
            "conversation_id": "sum-1",
            "conversation_summary": "Previous: a greeting.",
            "messages": messages,
        }
    )
    assert result is not None
    sent = fake.invocations[0][1].content
    assert "user-0" in sent
    assert f"user-{14 - 1}" not in sent  # latest human is inside KEEP_RECENT
    assert "Previous: a greeting." in sent
    remaining = [msg for msg in messages if msg not in result["summarized_messages"]]
    assert pending_summary_messages({"messages": remaining}) == []
