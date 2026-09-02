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
#   copy .env.example .env   # optional OPENAI_API_KEY fallback; the UI can also send a key
#   uvicorn app:app --reload --port 8000
#
# Chat requests may include header X-OpenAI-Api-Key. That key is used for the turn
# and is never written to conversation JSON. If the header is missing, OPENAI_API_KEY
# from the environment is used. Placeholder values like replace-me are ignored.
#
# Conversation JSON is stored under agent/conversations/.
#
# Tests (from this folder):
#   pip install -r requirements.txt pytest
#   pytest
#
# LLM usage is logged at INFO as:
#   [LLM] model=... node=call_llm input=... cached=... output=... total=... latency=... turn_calls=...
# Cost estimates are optional; set LLM_PRICE_*_PER_MILLION or LLM_MODEL_PRICING_JSON.
