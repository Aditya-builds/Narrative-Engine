from langchain_core.messages import AIMessage

from app.schemas import ChatRequest
from app.service import MISSING_API_KEY_DETAIL, run_chat
from graph.model import has_server_api_key
from tests.helpers import FakeChat


def _chat_request(**overrides) -> ChatRequest:
    payload = {
        "message": "Hello there.",
        "character": "Aurora",
        "persona": "Laxus",
        "reply_length": "medium",
    }
    payload.update(overrides)
    return ChatRequest(**payload)


def _world_stub(*_args, **_kwargs):
    return (
        {"identity": {"name": "Aurora", "class": "mage"}},
        {"identity": {"name": "Laxus", "class": "melee"}},
        [],
    )


def _patch_llm(monkeypatch, factory=None):
    monkeypatch.setattr("graph.nodes.load_world", _world_stub)
    monkeypatch.setattr("graph.model.ChatOpenAI", factory or FakeChat)


def test_chat_without_api_key_is_rejected(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)

    class BoomGraph:
        def invoke(self, _state):
            raise AssertionError("graph should not run without a key")

    try:
        run_chat(BoomGraph(), _chat_request(), openai_api_key=None)
    except Exception as exc:
        assert getattr(exc, "status_code", None) == 401
        assert MISSING_API_KEY_DETAIL in str(exc.detail)
    else:
        raise AssertionError("expected HTTP 401")


def test_placeholder_env_key_is_rejected(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "replace-me")
    try:
        run_chat(object(), _chat_request(), openai_api_key="")
    except Exception as exc:
        assert getattr(exc, "status_code", None) == 401
    else:
        raise AssertionError("expected HTTP 401")


def test_has_server_api_key_ignores_placeholders(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "replace-me")
    assert has_server_api_key() is False


def test_has_server_api_key_when_env_is_set(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "test-env-from-dotenv")
    assert has_server_api_key() is True


def test_env_key_is_used_when_header_is_missing(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "test-env-placeholder")
    built: list[FakeChat] = []

    def factory(**kwargs):
        model = FakeChat(AIMessage(content="A nod."), **kwargs)
        built.append(model)
        return model

    _patch_llm(monkeypatch, factory)
    monkeypatch.setattr("app.service.save_conversation", lambda _state: None)
    monkeypatch.setattr(
        "app.service.load_conversation",
        lambda _cid: {"messages": [], "conversation_summary": "", "important_memories": []},
    )

    from graph import compile_graph

    result = run_chat(compile_graph(), _chat_request(), openai_api_key=None)
    assert result.response
    assert built
    assert built[0].api_key == "test-env-placeholder"


def test_request_header_key_overrides_env_key(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "test-env-should-not-win")
    built: list[FakeChat] = []

    def factory(**kwargs):
        model = FakeChat(AIMessage(content="A nod."), **kwargs)
        built.append(model)
        return model

    _patch_llm(monkeypatch, factory)
    monkeypatch.setattr("app.service.save_conversation", lambda _state: None)
    monkeypatch.setattr(
        "app.service.load_conversation",
        lambda _cid: {"messages": [], "conversation_summary": "", "important_memories": []},
    )

    from graph import compile_graph

    result = run_chat(
        compile_graph(),
        _chat_request(),
        openai_api_key="test-header-placeholder",
    )
    assert result.response
    assert built
    assert built[0].api_key == "test-header-placeholder"


def test_request_header_key_is_used_for_the_model(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    built: list[FakeChat] = []

    def factory(**kwargs):
        model = FakeChat(AIMessage(content="A nod."), **kwargs)
        built.append(model)
        return model

    _patch_llm(monkeypatch, factory)
    monkeypatch.setattr("app.service.save_conversation", lambda _state: None)
    monkeypatch.setattr(
        "app.service.load_conversation",
        lambda _cid: {"messages": [], "conversation_summary": "", "important_memories": []},
    )

    from graph import compile_graph

    result = run_chat(
        compile_graph(),
        _chat_request(),
        openai_api_key="test-user-placeholder",
    )
    assert "nod" in result.response.lower() or result.response
    assert built
    assert built[0].api_key == "test-user-placeholder"
    assert "test-user-placeholder" not in str(result.model_dump())


def test_api_key_is_not_written_to_conversation_files(tmp_path, monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.setattr("memory.CONVERSATIONS_DIR", tmp_path)
    _patch_llm(monkeypatch)

    from graph import compile_graph
    from memory import load_conversation

    result = run_chat(
        compile_graph(),
        _chat_request(),
        openai_api_key="test-secret-do-not-store",
    )
    stored = load_conversation(result.conversation_id)
    blob = str(stored)
    assert "test-secret-do-not-store" not in blob
    assert "api_key" not in stored


def test_mock_llm_allows_chat_without_api_key(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.setenv("ENABLE_MOCK_LLM", "true")
    monkeypatch.setattr("graph.nodes.load_world", _world_stub)
    monkeypatch.setattr("app.service.save_conversation", lambda _state: None)
    monkeypatch.setattr(
        "app.service.load_conversation",
        lambda _cid: {"messages": [], "conversation_summary": "", "important_memories": []},
    )
    from graph import compile_graph

    result = run_chat(compile_graph(), _chat_request(), openai_api_key=None)
    assert result.response.startswith("[mock]")
