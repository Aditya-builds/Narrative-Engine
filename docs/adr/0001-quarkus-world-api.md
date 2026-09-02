# ADR 0001: Quarkus for the world API

## Status

Accepted

## Context

The world API reads and writes character and persona JSON under `World/`. It needs a small REST surface, fast local reload, and Jackson mapping that matches the existing file schema.

## Decision

Use Quarkus (JAX-RS + Jackson) instead of Spring Boot.

## Consequences

- Dev loop is `mvn quarkus:dev` on port 8080.
- REST resources stay thin; mapping and file IO live in loaders/mappers behind `EntityStore`.
- Spring-specific libraries (Spring MVC advice, Spring Cloud, etc.) are not used. JAX-RS `ExceptionMapper` / filters fill that role.
- A later swap to PostgreSQL can implement `EntityStore` without changing resource paths.
