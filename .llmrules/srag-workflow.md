---
description: Development workflow and tooling guidelines
---

# SRAG Development Workflow

## AI Assistant Behavior

### DO

- ✅ Use the Metals MCP server for Scala language support
- ✅ Read existing code before making changes
- ✅ Fix ALL linter warnings (zero warnings policy)
- ✅ Make code changes directly without lengthy explanations
- ✅ Follow hexagonal architecture boundaries strictly
- ✅ Update configuration when adding new adapters
- ✅ Write tests for new features

### Asynchronous Ingestion Pattern (Queue Port + Worker)

- The API persists the job via the Job Repository Port, then enqueues the job ID via the Job Queue Port.
- A background Worker consumes from the Job Queue Port and orchestrates processing through the Blob Store Port, Transcriber Port, Embedder Port, Database Port, and Vector Store Port.
- If enqueue fails, the API request fails (client can retry). No inline cleanup is performed.
- Operational jobs :
  - Daily cleanup: delete orphan blobs and remove stale jobs older than 24h via the relevant ports.

### DON'T

- ❌ NEVER write markdown files to explain what you did
- ❌ NEVER start the server yourself (no `sbt run` or `sbt infrastructure/run`)
- ❌ NEVER ignore linter warnings ("it compiles" is not enough)
- ❌ NEVER violate module dependencies (domain ← application ← infrastructure)
- ❌ NEVER put infrastructure concerns in domain or application modules

### DO (Builds & Checks)

- ✅ Compile to check for errors: `sbt compile` or `sbt infrastructure/compile`
- ✅ Run tests to verify changes: `sbt test`
- ✅ Check specific modules: `sbt domain/compile`, `sbt application/compile`
- ✅ **ALWAYS run after code changes**: `sbt scalafixAll scalafmtAll test`

## Build Commands (for reference only)

User will run these commands manually:

```bash
# Compile all modules
sbt compile

# Run tests
sbt test
```

## Development Workflow

### Adding a New Feature

1. **Start with domain**: Define new types in `modules/domain`
2. **Define ports**: Add port interfaces in `modules/application/ports`
3. **Create use case**: Implement orchestration in `modules/application/usecase`
4. **Implement adapters**: Add concrete implementations in `modules/infrastructure/adapters`
5. **Wire with ZIO**: Update `modules/infrastructure/runtime` with layers
6. **Configure**: Update `RuntimeConfig.scala`, `ConfigLoader.scala`, `AdapterFactory.scala`

### Changing an Adapter

1. Update the implementation in `modules/infrastructure/adapters/driven/[type]/[adapter]/`
2. If adding new config options, update `RuntimeConfig.scala`
3. Update parser in `ConfigLoader.scala` if config schema changed
4. Test with different configurations via `application.conf`

### Fixing Linter Errors/Warnings

- Address ALL warnings before considering a change complete
- Common issues:
  - Unused imports: Remove them
  - Unused variables: Use `_` prefix or remove
  - Type inference: Add explicit types for public APIs
  - Exhaustive pattern matching: Add missing cases or use wildcard with comment

## File Organization

### Where to put new files

| Type            | Location                                                                                   |
| --------------- | ------------------------------------------------------------------------------------------ |
| Domain entity   | `modules/domain/src/main/scala/srag/domain/`                                               |
| Domain error    | `modules/domain/src/main/scala/srag/domain/errors/`                                        |
| Port interface  | `modules/application/src/main/scala/srag/application/ports/[driving\|driven]/`             |
| Use case        | `modules/application/src/main/scala/srag/application/usecase/`                             |
| Driving adapter | `modules/infrastructure/src/main/scala/srag/infrastructure/adapters/driving/[grpc\|http]/` |
| Driven adapter  | `modules/infrastructure/src/main/scala/srag/infrastructure/adapters/driven/[type]/[tech]/` |
| Config types    | `modules/infrastructure/src/main/scala/srag/infrastructure/config/RuntimeConfig.scala`     |
| Config loader   | `modules/infrastructure/src/main/scala/srag/infrastructure/config/ConfigLoader.scala`      |
| Adapter factory | `modules/infrastructure/src/main/scala/srag/infrastructure/config/AdapterFactory.scala`    |
| Runtime wiring  | `modules/infrastructure/src/main/scala/srag/infrastructure/runtime/`                       |

## Code Quality Standards

- Zero compiler warnings
- Zero linter warnings
- All public APIs have explicit type signatures
- All domain types are immutable
- All effects are explicit (wrapped in ZIO)
- All errors are typed (domain ADTs)
- All configurations use HOCON + type-safe parsing

## Git Workflow

This is an **open source project**:

- Write clean, maintainable code
- Follow the established architecture patterns
- Keep commits focused and atomic
- Add tests for new functionality
- Don't write documentation files to explain what you did
- Don't overcomment your code
- Always write in english, even if the code or prompt is in another language
