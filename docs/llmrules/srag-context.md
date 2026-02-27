---
description: On-demand project context for SRAG product scope, runtime setup, and delivery constraints
---

# SRAG Project Context

## Product Scope

- SRAG is an open-source, scalable RAG microservice.
- It is designed for integration into external stacks, not as a standalone end-user product.
- Main capabilities:
  - Hybrid retrieval (vector + lexical BM25 + reranking).
  - Ingestion pipelines for text, audio, and evolving document/image support.
  - Metadata-aware ingestion and retrieval filtering.
  - Configurable adapters selected at runtime through configuration.

## Technology Context

- Language/runtime: Scala 3 + ZIO.
- API layer: Tapir + ZIO HTTP server stack.
- Storage and services used in the ecosystem include Postgres, Qdrant, OpenSearch, Redis, S3/MinIO-compatible blob storage, and external ML services (transcription/embedding/reranking).
- Build tool: sbt (multi-module project).

## Operational Context

- The system is intended for stateless horizontal scaling.
- Integrations are controlled by configuration and environment variables.
- Default local stack relies on Docker Compose services.
- Startup should validate config and fail fast when required dependencies are invalid.

## Delivery and Quality Context

- Keep module boundaries strict and avoid cross-layer leakage.
- Keep adapter implementations swappable through ports and factory/config wiring.
- Preserve typed error semantics through application flow and map them at boundaries.
- Prioritize deterministic behavior, observability, and resource safety in all runtime code.
