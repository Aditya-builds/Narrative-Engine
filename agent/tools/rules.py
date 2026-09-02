from constants import RELATIONSHIP_EVENTS, SEVERITIES, STAT_EVENTS, STAT_KEYS


def relationship_delta(event: str, severity: str) -> int:
    key = (_norm(event), _severity(severity))
    if key not in RELATIONSHIP_EVENTS:
        allowed = sorted({name for name, _sev in RELATIONSHIP_EVENTS})
        raise ValueError(f"Unknown relationship event '{event}'. Use one of: {', '.join(allowed)}")
    return RELATIONSHIP_EVENTS[key]


def stat_delta(event: str, severity: str) -> int:
    key = (_norm(event), _severity(severity))
    if key not in STAT_EVENTS:
        allowed = sorted({name for name, _sev in STAT_EVENTS})
        raise ValueError(f"Unknown stat event '{event}'. Use one of: {', '.join(allowed)}")
    return STAT_EVENTS[key]


def clamp_score(value: int) -> int:
    return max(0, min(100, int(value)))


def require_stat(attribute: str) -> str:
    attr = _norm(attribute)
    if attr not in STAT_KEYS:
        raise ValueError(f"attribute must be one of {', '.join(STAT_KEYS)}")
    return attr


def _severity(value: str) -> str:
    severity = _norm(value)
    if severity not in SEVERITIES:
        raise ValueError("severity must be 'minor' or 'major'")
    return severity


def _norm(value: str) -> str:
    return (value or "").strip().lower().replace(" ", "_")
