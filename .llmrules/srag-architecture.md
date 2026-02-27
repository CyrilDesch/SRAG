---
description: On-demand SRAG architecture rules covering module boundaries, ports/adapters, and wiring workflow
---

# SRAG Architecture Rules

## High-Level Architecture

- SRAG follows hexagonal architecture (ports and adapters).
- Dependency direction is strict:

```text
srag-domain <- srag-application <- srag-infrastructure
```

- Never introduce reverse dependencies.

## Module Responsibilities

### `srag-domain`

- Pure domain model and domain rules.
- Immutable business types and ADTs.
- No infrastructure concerns, no framework-specific I/O code.

### `srag-application`

- Use-case orchestration and port interfaces.
- Business workflows composed from ports.
- No concrete adapter code.

### `srag-infrastructure`

- Concrete driving and driven adapters.
- Runtime wiring with layers, configuration loading, and external integrations.
- Protocol mapping (HTTP/transport), observability, lifecycle startup/shutdown.

## Port and Adapter Rules

- Define ports in the application module.
- Implement adapters in infrastructure only.
- Keep adapters thin: translate protocol/data/SDK concerns into domain-friendly inputs/outputs.
- Map external failures into domain/application error types at adapter boundaries.
- Keep transport mapping (status codes, payload shapes) in driving adapters.

## Configuration and Wiring

- Use typed runtime configuration models.
- Keep adapter selection declarative (config-driven).
- Centralize wiring in runtime/config composition.
- Ensure each long-lived integration is managed as a scoped resource.

## Testing by Layer

- Domain: pure unit tests for invariants and behavior.
- Application: effect tests for workflow orchestration and error handling.
- Infrastructure: no tests.
