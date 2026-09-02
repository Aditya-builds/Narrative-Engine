# ADR 0005: Local quality gates

## Status

Accepted

## Context

The repo is polyglot. CI must stay cheap and fail on secrets and regressions without requiring Docker deployment.

## Decision

- GitHub Actions runs secret scan, Quarkus tests (JaCoCo 90%), agent pytest, and an Angular production build in parallel.
- Dependabot watches Maven, pip, npm, and GitHub Actions.
- Java coverage is a hard gate. Python coverage is reported as an artifact, not a fail-the-build threshold, until the suite is denser.
- Branch protection, Pact contract tests, Playwright E2E, Spotless/Ruff/Prettier-as-CI-fail, and husky are documented here and not enforced yet.

## Consequences

- Contributors run `.\start-dev.ps1` (or `./start-dev.sh`) for the three local processes.
- `pre-commit` is optional (`pip install pre-commit && pre-commit install` from the repo root).
- GitHub branch protection (CI required, no coverage drop) is a repo-admin setting, not something the codebase can enable.
