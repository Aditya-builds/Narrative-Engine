#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")" && pwd)"

start_tab() {
  local title="$1"
  local dir="$2"
  local cmd="$3"
  if command -v osascript >/dev/null 2>&1; then
    osascript -e "tell application \"Terminal\" to do script \"cd '$dir' && $cmd\""
  else
    (cd "$dir" && bash -lc "$cmd") &
  fi
  echo "started $title"
}

start_tab "Quarkus :8080" "$root/backend/narrative-engine" "./mvnw quarkus:dev"
start_tab "Agent :8000" "$root/agent" "[ -x .venv/bin/python ] || python3 -m venv .venv; .venv/bin/python -m uvicorn app:app --reload --port 8000"
start_tab "Angular :4200" "$root/frontend" "npm start"

echo "UI:      http://localhost:4200"
echo "Quarkus: http://localhost:8080/q/health"
echo "Agent:   http://localhost:8000/health"
echo "Set ENABLE_MOCK_LLM=true in agent/.env to chat without OpenAI credits."
