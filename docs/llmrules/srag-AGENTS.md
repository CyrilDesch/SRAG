---
description: Always-applied SRAG Scala 3 + ZIO development rules and engineering standards
---

# SRAG Development Rules

## Core Engineering Principles

- Follow **SOLID**, **DRY**, **KISS**, and **YAGNI**.
- Keep changes minimal, explicit, and reversible.
- Break work into the smallest valid units and solve step-by-step.
- Keep business logic pure and isolate side effects.
- Always compile and run tests after every code change.

## Other rules

- `.llmrules/srag-architecture.md` (On-demand SRAG architecture rules covering module boundaries, ports/adapters, and wiring workflow)
- `.llmrules/srag-context.md` (On-demand project context for SRAG product scope, runtime setup, and delivery constraints)

## Security and Reliability Baseline

- Follow OWASP principles for input validation, authentication, authorization, logging, and secret handling.
- Never leak secrets or sensitive internals in logs, errors, or API responses.
- Validate all external input at boundaries (HTTP, queue, DB, file, config).
- Fail fast on invalid configuration at startup.
- Keep secrets in environment variables or secret stores, never in source code.

## Scala 3 Domain Modeling

- Model domain concepts with strong types, not primitive `String`/`Int`/`Boolean` values.
- Prefer Scala 3 `opaque type` wrappers for zero-cost domain types.
- Use smart constructors for all untrusted input.
- Separate validation from pure domain operations.
- Replace boolean blindness with ADTs (`enum` or sealed traits) when meaning matters.
- Keep domain models immutable (`final case class`, `enum`, pure values).

## Newtypes and Validation (Scala 3 + ZIO)

- Prefer `opaque type` over value classes for domain wrappers.
- Combine opaque types with predicate validation (for example Iron or Refined-style validation) where needed.
- Use runtime validation for external inputs and return typed domain errors.
- Accumulate multiple validation errors with `zio.prelude.Validation` (or equivalent), not fail-fast `Either`, when UX requires full error reporting.
- Use fail-fast semantics only when business logic requires short-circuit behavior.

## ZIO Effect Design

- Use explicit effect types in public APIs.
- Prefer the least powerful alias that matches the contract: `UIO`, `URIO`, `IO`, `Task`, `RIO`, then raw `ZIO` only when needed.
- Keep the environment `R` and error type `E` as narrow as possible.
- Prefer typed domain errors in `E` for expected business failures.
- Treat defects (`die`) as truly unexpected programming or infrastructure faults.
- Do not perform side effects outside ZIO effects.
- Keep constructors pure. Do not hide side effects in constructors. :contentReference[oaicite:1]{index=1}

## Error Handling (Adapted to ZIO)

- Default to `ZIO[R, E, A]` and propagate errors naturally.
- Only encode errors in success values (for example `Either`) when callers must branch on multiple outcomes in the success channel.
- Prefer these combinators: `catchAll`, `catchSome`, `mapError`, `foldZIO`, `either`, `orElse`, `tapError`, `retry`.
- Map technical errors to domain errors at the correct boundary.
- Keep the happy path readable. Recover only where the business needs custom behavior.
- Translate domain errors to transport semantics (HTTP, gRPC) in driving adapters.
- Return generic external auth errors when necessary to avoid user or account enumeration.
- For recoverable transient failures, use `Schedule`-based retries with bounded attempts and backoff.
- For non-critical side effects, explicitly isolate failures (`.either.unit` or equivalent) only when business-approved.

## Domain Error Modeling

- Model business failures as small ADTs.
- For custom ADT-style errors, prefer extending `NoStackTrace`.
- Use descriptive error names and carry only useful, non-sensitive context.
- Avoid raw `Throwable` in domain APIs.

## ZIO Coding Conventions

- Traits should generally extend `Serializable`.
- ADT root traits should generally extend `Product` and `Serializable`.
- If a class has all members `final`, make the class itself `final` and remove redundant `final` member modifiers except for constants.
- Use `def` instead of abstract `val` members to avoid initialization-order null issues.
- Avoid overloading standard interface names for your own services when it harms clarity.
- Follow established ZIO naming patterns such as `fromX`, `zipPar`, `foreachPar`, `...Discard`, and `unsafe...`.
- Operators returning effects should be lazy in non-lambda parameters unless strictness is explicitly required for performance or signature reasons.
- Keep parameter names conventional where it improves readability, such as `pf` for partial functions and `ev` for evidence parameters.
- Add precise type annotations for generalized ADTs and type aliases.
- Keep shared library or framework-like APIs documented with ScalaDoc where they are reused broadly. :contentReference[oaicite:2]{index=2}

## Tagless-Final and Layered Architecture

- Define capabilities as algebras (`trait X[F[_]]` style) or service traits in ZIO style.
- Keep business interfaces free from infrastructure details.
- Put implementation constraints in interpreters, not in core interfaces.
- Prefer explicit dependency passing (constructor, module, layer wiring) over large implicit algebra lists.
- Group related dependencies into modules when constructor signatures grow.

## Dependency Injection and Layers

- Use `ZLayer` to wire long-lived services and infrastructure resources.
- Prefer `ZLayer.scoped` for resources with acquisition and release lifecycles.
- Remember that layers provided globally are shared and memoized by default.
- Remember that layers provided locally are not memoized by default.
- Use `memoize` when a locally provided layer must be reused safely within a scope.
- Use `fresh` when you explicitly want a non-shared instance.
- In Scala 3, prefer the most direct and readable `provide` style available, including `provideSomeAuto` where it improves clarity. :contentReference[oaicite:3]{index=3}

## State and Concurrency

- Encapsulate mutable state in interpreters or adapters, not in business interfaces.
- Use the right abstraction:
  - Local sequential state: pure values or local effect state.
  - Shared concurrent state: `Ref`, `TRef`, `Queue`, `Hub`, `Semaphore`, STM structures.
- Scope shared state tightly. Avoid global mutable state.
- Supervise background fibers. Prefer scoped lifecycles (`forkScoped`, managed background workers).
- Define clear cancellation boundaries.
- Use `uninterruptible` and `uninterruptibleMask` only when there is a clear need and restore interruptibility as soon as possible.

## Interruption and Fiber Lifecycle

- Assume fibers are interruptible by default unless explicitly masked.
- Remember that `Fiber#interrupt` waits for finalizers to complete.
- Use `disconnect` or `interruptFork` when interruption must not block the caller while cleanup continues in the background.
- Treat interruption boundaries as part of the design for all long-running or background work.
- Never rely on untracked fibers that outlive their ownership scope. :contentReference[oaicite:4]{index=4}

## Resource Safety

- Treat lifecycle components as managed resources.
- Use `ZLayer.scoped`, `Scope`, and `ZIO.acquireRelease` for acquisition and cleanup safety.
- Open clients, pools, servers, and long-lived workers through scoped resource management.
- Do not spawn untracked fibers that outlive their ownership scope.

## Blocking and External Calls

- Handle blocking explicitly with `ZIO.attemptBlocking` or dedicated executors, never on compute threads.
- Do not assume interrupting `attemptBlocking` interrupts the underlying JVM thread.
- Use `attemptBlockingInterrupt` when JVM thread interruption is required and supported by the blocking API.
- Use `attemptBlockingCancelable` when the blocking API needs a custom cancellation signal or shutdown action.
- Add timeouts to external calls and health checks where failure must be bounded. :contentReference[oaicite:5]{index=5}

## API and Adapter Design

- Start from business requirements before endpoint or storage design.
- Keep external contracts explicit and versioned when needed.
- Validate request payloads, query params, and path params at the edge.
- Keep route handlers clear. Avoid clever abstractions that hide success and failure branches.
- Compose endpoints and middleware functionally (Tapir or ZIO HTTP composition patterns).
- Map domain outcomes to precise status and error responses.

## Collection and Interface Semantics

- Accept the most general input type that satisfies the contract.
- Return the most specific output type that communicates semantics clearly.
- Choose concrete collection types deliberately (`List`, `Vector`, `Chunk`, `NonEmptyChunk`, streams) based on behavior and performance needs.
- Keep optionality and failure explicit in API types. :contentReference[oaicite:6]{index=6}

## Dependencies and Capabilities

- Be conservative with new dependencies. Prefer local abstractions when enough.
- Wrap side-effectful utilities (UUID, time, random, system) behind capability traits or services.
- Keep typeclass instances and codecs close to owned types (companions where possible).
- Limit orphan instances and centralize them consistently when unavoidable.

## Persistence and Transactions

- Prefer asynchronous, resource-safe data-access libraries and clients.
- Use connection or session pools for concurrent workloads.
- Model transactions as scoped operations.
- Keep atomic workflows inside one transactional boundary.

## Parallelism and Performance

- Run independent effects in parallel (`zipPar`, `collectAllPar`, `foreachPar`) when safe.
- Use parallelism only when operations are independent and idempotency or partial-failure semantics are understood.
- Keep adapters instantiated once via layer composition and shared safely.

## Testing Standards

- Use effect-native testing tools. ZIO Test is the default.
- Keep domain tests pure and deterministic.
- Prefer assertions on values produced by effects, not ad hoc side effects.
- Add property-based tests and law-style tests when they significantly raise confidence.
- Use typed equality and deterministic test fixtures where possible.
- Remember that ZIO Test suites run in parallel by default.
- Use `TestAspect.sequential` when order matters or shared state makes parallelism unsafe.
- Use `parallelN` when you want bounded concurrency instead of fully parallel execution.
- Prefer built-in test services such as `TestClock`, `TestConsole`, `TestRandom`, `TestSystem`, and `Live` instead of ad hoc mocks for core ZIO capabilities. :contentReference[oaicite:7]{index=7}

## Build and Delivery Discipline

- Compile and run tests after every code change.
- Always run sbt scalafmt fix/check and test.
- Keep formatter, linter, compile, and test in CI and local workflows.
- Favor reproducible builds and automated delivery pipelines.
- Keep operational behavior explicit (config profiles, startup checks, health checks).

## Practical Cats-to-ZIO Adaptation Notes

- `Resource` mindset maps to `Scope`, `ZLayer.scoped`, and `acquireRelease`.
- `EitherT` or `OptionT`-heavy APIs should usually become direct `ZIO` error or option modeling.
- Error-typeclass patterns map to ZIO error-channel operators.
- `parMapN` and parallel validation ideas map to ZIO parallel combinators and `zio.prelude.Validation`.