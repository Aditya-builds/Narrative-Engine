from langchain_core.messages import AIMessage, HumanMessage

from graph import compile_graph
from graph.limits import BUDGET_EXCEEDED_FALLBACK, MAX_LLM_CALLS_PER_TURN
from graph.nodes import call_llm, update_memory
from graph.summary import pending_summary_messages
from tests.helpers import FakeChat, sample_turn_state


def _world_stub(*_args, **_kwargs):
    return (
        {"identity": {"name": "Aurora", "class": "mage"}},
        {"identity": {"name": "Laxus", "class": "melee"}},
        [],
    )


def test_normal_turn_makes_one_llm_call(monkeypatch):
    fake = FakeChat(AIMessage(content="She tilts her head. 'Hello.'"))
    monkeypatch.setattr("graph.nodes.chat_model", lambda **kwargs: fake)
    monkeypatch.setattr("graph.nodes.load_world", _world_stub)

    graph = compile_graph()
    result = graph.invoke(sample_turn_state())

    assert len(fake.invocations) == 1
    assert result["llm_calls_this_turn"] == 1
    assert "Hello" in result["response"]


def test_update_memory_does_not_call_llm(monkeypatch):
    monkeypatch.setattr(
        "graph.model.chat_model",
        lambda **kwargs: (_ for _ in ()).throw(AssertionError("summarizer ran on the reply path")),
    )
    messages = []
    for index in range(15):
        messages.append(HumanMessage(content=f"hello {index}"))
        messages.append(AIMessage(content=f"reply {index}"))
    result = update_memory({"messages": messages, "important_memories": []})
    assert result["response"] == "reply 14"
    assert pending_summary_messages({"messages": messages})


def test_llm_budget_stops_additional_calls(monkeypatch):
    fake = FakeChat(AIMessage(content="should not run"))
    monkeypatch.setattr("graph.nodes.chat_model", lambda **kwargs: fake)

    result = call_llm(
        sample_turn_state(llm_calls_this_turn=MAX_LLM_CALLS_PER_TURN)
    )

    assert fake.invocations == []
    assert result["messages"][0].content == BUDGET_EXCEEDED_FALLBACK
    assert result["llm_calls_this_turn"] == MAX_LLM_CALLS_PER_TURN


def test_graph_tool_loop_stops_at_budget(monkeypatch):
    calls = {"n": 0}

    def looping_reply(_messages):
        calls["n"] += 1
        return AIMessage(
            content="",
            tool_calls=[
                {
                    "name": "remember_event",
                    "args": {"fact": f"loop-{calls['n']}"},
                    "id": f"call_{calls['n']}",
                    "type": "tool_call",
                }
            ],
        )

    fake = FakeChat(looping_reply)
    monkeypatch.setattr("graph.nodes.chat_model", lambda **kwargs: fake)
    monkeypatch.setattr("graph.nodes.load_world", _world_stub)

    graph = compile_graph()
    result = graph.invoke(sample_turn_state())

    assert calls["n"] == MAX_LLM_CALLS_PER_TURN
    assert len(fake.invocations) == MAX_LLM_CALLS_PER_TURN
    assert result["response"] == BUDGET_EXCEEDED_FALLBACK
    assert result["llm_calls_this_turn"] == MAX_LLM_CALLS_PER_TURN
