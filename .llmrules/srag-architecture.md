---
description: SRAG hexagonal architecture and module structure
---

# SRAG Hexagonal Architecture

## Module Structure and Dependencies

This project follows strict hexagonal architecture with three sbt modules:

```
domain  ←  application  ←  infrastructure
```

**Dependency Rule**: Each module only depends on layers to its left. Never reverse dependencies.

### modules/domain

- **Purpose**: Pure business model only
- **Contains**: Immutable domain types (`Transcript`, `Segment`, `IngestionJob`, metadata, domain events, errors)
- **Dependencies**: Scala stdlib only (+ lightweight helpers like `zio-prelude`)
- **Forbidden**: NO ZIO effects, NO gRPC, NO database types, NO external SDKs

### modules/application

- **Purpose**: Use cases and port definitions
- **Contains**:
  - Use cases orchestrating business workflows (`DefaultIngestPipeline`, `ReplayJob`)
  - **Driving ports** (`ports/driving/`): interfaces exposed to external world
  - **Driven ports** (`ports/driven/`): interfaces for outbound dependencies
- **Dependencies**: `domain` module + ZIO core for effects
- **Forbidden**: NO concrete adapters, NO infrastructure concerns

### modules/infrastructure

- **Purpose**: All adapters and runtime concerns
- **Contains**:
  - **Primary adapters** (`adapters/driving/`): gRPC, HTTP/REST, CLI entry points
  - **Secondary adapters** (`adapters/driven/`): Database, vector stores, transcription APIs, embedding services, blob storage
  - **Runtime** (`runtime/`): ZIO layer wiring, configuration loading, observability
- **Dependencies**: `application` module + all third-party SDKs
- **Rule**: Each adapter in its own package (easy to swap implementations)

## Adding New Features

Follow this workflow:

1. **Domain first**: Add entities/events/errors in `modules/domain`
2. **Define ports**: Create/update port interfaces in `modules/application/ports`
3. **Implement use case**: Add orchestration logic in `modules/application/usecase`
4. **Create adapters**: Implement concrete adapters in `modules/infrastructure/adapters`
5. **Wire runtime**: Update `modules/infrastructure/runtime` with ZIO layers
6. **Update config**: Extend configuration in `RuntimeConfig.scala` and `application.conf`

## Key Principles

- **Dependency Inversion**: Always code against ports (traits), implement in adapters
- **Domain Purity**: Keep domain model free from serialization/IO concerns
- **Error Modeling**: Errors are domain concepts; propagate through use cases; map to protocols in adapters
- **Testing**: Unit tests for domain/application; integration tests for adapters
