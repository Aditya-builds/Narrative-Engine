# ADR 0002: Split the LangGraph agent from Quarkus

## Status

Accepted

## Context

Chat needs an LLM, tools, and conversation memory. The world API needs stable file CRUD. Mixing both in one JVM would couple Python-first LangGraph to Java and make local LLM iteration slower.

## Decision

Keep a FastAPI + LangGraph service on port 8000. Angular talks to Quarkus for world data and to the agent for chat. The agent calls Quarkus over HTTP when tools need to update JSON.

## Consequences

- Three processes in local dev (Quarkus, agent, Angular).
- Correlation IDs (`X-Request-ID`) are required to trace one UI action across services.
- Circuit breakers on "Quarkus → Python" do not apply: that hop does not exist. Timeouts live on Angular → agent and agent → Quarkus.
- OpenAPI is generated separately (`/q/openapi` when enabled, FastAPI `/docs`).
