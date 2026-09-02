from contextvars import ContextVar

REQUEST_ID: ContextVar[str] = ContextVar("request_id", default="")


def current_request_id() -> str:
    return REQUEST_ID.get() or ""


def set_request_id(value: str) -> object:
    return REQUEST_ID.set(value or "")


def reset_request_id(token) -> None:
    REQUEST_ID.reset(token)
