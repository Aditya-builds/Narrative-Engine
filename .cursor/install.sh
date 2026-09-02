#!/usr/bin/env bash
# Idempotent dependency refresh for the Narrative Engine (Quarkus + Angular + FastAPI agent).
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

# The default image ships Python 3.12 but not the venv/pip bootstrap module.
if ! dpkg -s python3.12-venv >/dev/null 2>&1; then
  sudo apt-get update -qq
  sudo apt-get install -y --no-install-recommends python3.12-venv
fi

# Backend (Quarkus): prime the Maven wrapper and download dependencies.
(
  cd backend/narrative-engine
  chmod +x mvnw
  ./mvnw -B -q -DskipTests compile
)

# Frontend (Angular): install locked dependencies.
(
  cd frontend
  npm ci
)

# Agent (FastAPI + LangGraph): create the virtualenv and install dependencies.
(
  cd agent
  if [ ! -x .venv/bin/pip ]; then
    rm -rf .venv
    python3 -m venv .venv
  fi
  .venv/bin/pip install --upgrade pip
  .venv/bin/pip install -r requirements.txt
  # Provide a local .env (OPENAI_API_KEY comes from the injected secret when present).
  [ -f .env ] || cp .env.example .env
)

echo "Narrative Engine install complete."
