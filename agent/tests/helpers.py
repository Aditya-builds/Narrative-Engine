from langchain_core.messages import AIMessage, HumanMessage


class FakeChat:
    def __init__(self, reply=None, max_tokens=None, **kwargs):
        self.reply = reply if reply is not None else AIMessage(content="Hello.")
        self.max_tokens = max_tokens if max_tokens is not None else kwargs.get("max_tokens")
        self.kwargs = kwargs
        self.invocations: list[list] = []
        self.model_name = kwargs.get("model") or "gpt-test"
        self.model = self.model_name
        self.api_key = kwargs.get("api_key")

    def bind_tools(self, _tools):
        return self

    def invoke(self, messages):
        self.invocations.append(list(messages))
        if callable(self.reply):
            return self.reply(messages)
        return self.reply


def sample_turn_state(**overrides):
    state = {
        "conversation_id": "conv-test",
        "character_id": "Aurora",
        "persona_id": "Laxus",
        "user_message": "Hello there.",
        "reply_length": "medium",
        "relevant_context": "WORLD_CTX",
        "conversation_summary": "SUM_CTX",
        "important_memories": ["MEM_CTX"],
        "messages": [HumanMessage(content="Hello there.")],
        "llm_calls_this_turn": 0,
    }
    state.update(overrides)
    return state
