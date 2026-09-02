from langchain_core.messages import AIMessage


class MockChatModel:
    """Deterministic stand-in used when ENABLE_MOCK_LLM is on."""

    def __init__(self, **kwargs):
        self.kwargs = kwargs
        self.model_name = kwargs.get("model") or "mock-llm"
        self.model = self.model_name
        self.api_key = kwargs.get("api_key")
        self.max_tokens = kwargs.get("max_tokens")
        self.invocations: list = []

    def bind_tools(self, _tools):
        return self

    def invoke(self, messages):
        self.invocations.append(list(messages))
        last = messages[-1] if messages else None
        text = getattr(last, "content", "") if last is not None else ""
        spoken = text.strip() or "Hello."
        return AIMessage(content=f"[mock] {spoken}")
