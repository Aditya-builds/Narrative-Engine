# ADR 0003: File-based World storage

## Status

Accepted

## Context

Characters and personas are authored as folders of JSON. Git-friendly files matter more than multi-user transactions for this stage of the project.

## Decision

Store world state under `World/Characters/{Name}/` and `World/Persona/{Name}/`. Quarkus is the writer. The agent never writes those files directly; it updates them through the world API.

## Consequences

- Writes use a lock file plus temp-file rename so readers never see a half-written JSON document.
- Java and Python still must not both write `World/` later without the same locking protocol.
- `EntityStore` is the seam for a future PostgreSQL or MongoDB implementation.
- Chat threads live separately under `agent/chats/` (also atomic replace) so UI history survives agent reloads without touching `World/`.
