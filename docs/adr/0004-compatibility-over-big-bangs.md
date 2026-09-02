# ADR 0004: Compatibility over big-bang API changes

## Status

Accepted

## Context

Industry practice wants `/api/v1/` prefixes, generated Angular clients, and SSE/WebSocket token streaming. The live UI already depends on `/characters`, `/create_new_character/...`, `/chat`, and `{ error }` / `{ detail }` payloads.

## Decision

Do not rename existing routes in this pass. Add a shared error envelope *on top of* `error` and `detail`. Keep POST `/chat` as a complete-reply call.

Deferred, in this order:

1. Generate the Angular HTTP client from Quarkus OpenAPI (`/q/openapi`) once SmallRye OpenAPI is added.
2. Dual-mount `/api/v1/...` aliases, then retire unversioned paths.
3. Add FastAPI `StreamingResponse` + Angular `EventSource` (or fetch streams) for token-by-token chat. Until then, the 120s HTTP timeout covers a full reply.

## Consequences

- No broken Angular proxy or bookmarks.
- Error clients can read `message`, `error`, or `detail`.
- SSE remains the highest-ROI UX change once the contract is frozen.
