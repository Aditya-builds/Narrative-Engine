import json
import logging
import sys
from pathlib import Path

from dotenv import load_dotenv

from observability import current_request_id

ROOT = Path(__file__).resolve().parent
load_dotenv(ROOT / ".env")

CONVERSATIONS_DIR = ROOT / "conversations"
CHATS_DIR = ROOT / "chats"
USAGE_LOG_PATH = ROOT / "llm_usage.jsonl"


class RequestIdFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.request_id = current_request_id() or "-"
        return True


class JsonLogFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload = {
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "requestId": getattr(record, "request_id", "-"),
        }
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)
        return json.dumps(payload, ensure_ascii=True)


def _json_logs_enabled() -> bool:
    import os

    return os.getenv("NARRATIVE_JSON_LOGS", "").strip().lower() in {"1", "true", "yes", "on"}


def configure_logging() -> None:
    formatter: logging.Formatter
    if _json_logs_enabled():
        formatter = JsonLogFormatter()
    else:
        formatter = logging.Formatter("%(levelname)s requestId=%(request_id)s %(message)s")

    for name in ("narrative", "narrative.llm"):
        log = logging.getLogger(name)
        log.setLevel(logging.INFO)
        log.handlers.clear()
        handler = logging.StreamHandler(sys.stderr)
        handler.setLevel(logging.INFO)
        handler.addFilter(RequestIdFilter())
        handler.setFormatter(formatter)
        log.addHandler(handler)
        log.propagate = False


configure_logging()
