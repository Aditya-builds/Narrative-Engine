from datetime import datetime, timezone

from fastapi import HTTPException, Request
from fastapi.responses import JSONResponse

ERROR_CODES = {
    400: "BAD_REQUEST",
    401: "UNAUTHORIZED",
    403: "FORBIDDEN",
    404: "NOT_FOUND",
    409: "CONFLICT",
    429: "RATE_LIMITED",
    500: "INTERNAL_ERROR",
    502: "BAD_GATEWAY",
    503: "UNAVAILABLE",
    504: "TIMEOUT",
}


def http_error_from_exception(exc: Exception) -> HTTPException:
    text = str(exc).lower()
    name = type(exc).__name__.lower()
    blob = f"{name} {text}"
    if any(token in blob for token in ("auth", "api key", "invalid_api_key", "unauthorized")):
        return HTTPException(
            status_code=401,
            detail="That OpenAI API key was rejected. Check it and try again.",
        )
    if any(token in blob for token in ("insufficient_quota", "quota", "billing")):
        return HTTPException(
            status_code=503,
            detail="The storyteller is out of credits right now.",
        )
    if any(token in blob for token in ("rate limit", "ratelimit", "429", "too many requests")):
        return HTTPException(
            status_code=429,
            detail="Too many replies at once. Wait a few seconds and try again.",
        )
    if any(token in blob for token in ("timeout", "timed out", "deadline")):
        return HTTPException(
            status_code=504,
            detail="The character took too long to reply. Try again.",
        )
    if any(token in blob for token in ("connection", "connecterror", "network", "refus")):
        return HTTPException(
            status_code=503,
            detail="Could not reach the language model.",
        )
    if "not found" in blob:
        return HTTPException(
            status_code=404,
            detail="That character or persona could not be loaded.",
        )
    if any(token in blob for token in ("invalid_request", "unsupported", "reasoning_effort")):
        return HTTPException(
            status_code=502,
            detail="The language model rejected this request. Try sending again.",
        )
    return HTTPException(
        status_code=503,
        detail="The character could not reply. Try again in a moment.",
    )


def error_message(detail: object) -> str:
    if isinstance(detail, str) and detail.strip():
        return detail
    if isinstance(detail, list) and detail:
        first = detail[0]
        if isinstance(first, dict) and first.get("msg"):
            return str(first["msg"])
        return str(first)
    if detail:
        return str(detail)
    return "Request failed"


def error_payload(request: Request | None, status: int, message: str) -> dict:
    text = message.strip() if isinstance(message, str) and message.strip() else "Request failed"
    path = request.url.path if request is not None else "/"
    return {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "path": path,
        "status": status,
        "errorCode": ERROR_CODES.get(status, "ERROR"),
        "message": text,
        "error": text,
        "detail": text,
    }


def error_response(
    request: Request | None, status: int, message: str, headers: dict | None = None
) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        content=error_payload(request, status, message),
        headers=headers or {},
    )


def json_from_http_exception(request: Request, exc: HTTPException) -> JSONResponse:
    headers = dict(exc.headers or {})
    return error_response(request, exc.status_code, error_message(exc.detail), headers)


def json_from_exception(request: Request, exc: Exception) -> JSONResponse:
    if isinstance(exc, HTTPException):
        return json_from_http_exception(request, exc)
    return json_from_http_exception(request, http_error_from_exception(exc))
