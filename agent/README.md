# Narrative Engine agent service
#
#   agent/
#     app/       FastAPI HTTP (POST /chat)
#     graph/     LangGraph orchestration
#     tools/     constrained Quarkus operations
#     memory/    conversation JSON
#     world.py   Quarkus client + compact context
#
# Run from this folder:
#   python -m venv .venv
#   .\.venv\Scripts\Activate.ps1
#   pip install -r requirements.txt
#   copy .env.example .env   # optional OPENAI_API_KEY; the UI can also send a personal key
#   uvicorn app:app --reload --port 8000
#
# GET /llm-config and GET /chat/config report whether a real OPENAI_API_KEY is set (never the key itself).
# Chat requests may include header X-OpenAI-Api-Key. That key is used for the turn
# and is never written to conversation JSON. If the header is missing, OPENAI_API_KEY
# from the environment is used. Placeholder values like replace-me are ignored.
#
# Conversation JSON is stored under agent/conversations/.
# UI chat threads are stored under agent/chats/{Character}.json and survive uvicorn --reload.
# LLM usage is appended to agent/llm_usage.jsonl (one JSON object per model call).
# Those files are gitignored. No prompts or API keys are stored in the usage log.
#
# Tests (from this folder):
#   pip install -r requirements.txt pytest
#   pytest
#
# Each call is also printed in the uvicorn terminal:
#   [LLM] model=... node=call_llm input=... cached=... output=... total=... latency=... turn_calls=...
# followed by an LLM Usage block.
# Cost estimates are optional; set LLM_PRICE_*_PER_MILLION or LLM_MODEL_PRICING_JSON.
