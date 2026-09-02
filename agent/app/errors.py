from fastapi import HTTPException


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
