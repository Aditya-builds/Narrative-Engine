import logging
import sys
from pathlib import Path

from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parent
load_dotenv(ROOT / ".env")

CONVERSATIONS_DIR = ROOT / "conversations"
CHATS_DIR = ROOT / "chats"
USAGE_LOG_PATH = ROOT / "llm_usage.jsonl"


def configure_logging() -> None:
    log = logging.getLogger("narrative.llm")
    log.setLevel(logging.INFO)
    if not log.handlers:
        handler = logging.StreamHandler(sys.stderr)
        handler.setLevel(logging.INFO)
        handler.setFormatter(logging.Formatter("%(levelname)s:     %(message)s"))
        log.addHandler(handler)
    log.propagate = False


configure_logging()
