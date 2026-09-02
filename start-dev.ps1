# Start Quarkus, the Python agent, and Angular in three windows.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

function Start-DevWindow([string]$title, [string]$workdir, [string]$command) {
    Start-Process powershell -WorkingDirectory $workdir -ArgumentList @(
        "-NoExit",
        "-Command",
        "Write-Host '$title'; $command"
    )
}

Start-DevWindow "Quarkus :8080" (Join-Path $root "backend\narrative-engine") ".\mvnw.cmd quarkus:dev"
Start-DevWindow "Agent :8000" (Join-Path $root "agent") "if (-not (Test-Path .\.venv\Scripts\python.exe)) { python -m venv .venv }; .\.venv\Scripts\python.exe -m uvicorn app:app --reload --port 8000"
Start-DevWindow "Angular :4200" (Join-Path $root "frontend") "npm.cmd start"

Write-Host "Started three terminals."
Write-Host "UI:      http://localhost:4200"
Write-Host "Quarkus: http://localhost:8080/q/health"
Write-Host "Agent:   http://localhost:8000/health"
Write-Host "Set ENABLE_MOCK_LLM=true in agent/.env to chat without OpenAI credits."
