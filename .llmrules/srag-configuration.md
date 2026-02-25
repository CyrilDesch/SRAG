---
description: Declarative adapter configuration system
---

# Declarative Adapter Configuration

## Configuration Architecture

SRAG uses declarative configuration for all adapters via `application.conf`:

```
application.conf → ConfigLoader → RuntimeConfig → AdapterFactory → Concrete Adapters
```

**Benefits**: Change implementations without recompiling; different profiles per environment; type-safe configuration.

## Environment-Specific Configs

Use config profiles for different environments:

- **Dev**: `application.conf` (default)
- **Test**: `application-test.conf` (run with `-Dconfig.resource=application-test.conf`)
- **Prod**: `application-prod.conf` (run with `-Dconfig.resource=application-prod.conf`)

## Environment Variables

Use HOCON substitution:

- Required: `${VAR_NAME}` - fails if missing
- Optional: `${?VAR_NAME}` - uses default if missing

## Requirements Profiles

| Environment | Database | Vector Store | Transcription | Blob Store | API  |
| ----------- | -------- | ------------ | ------------- | ---------- | ---- |
| Dev Local   | Postgres | Qdrant       | AssemblyAI    | Local      | gRPC |
| CI Tests    | MySQL    | Qdrant       | Whisper       | Local      | gRPC |
| Staging     | Postgres | Pinecone     | AssemblyAI    | S3         | gRPC |
| Production  | Postgres | Pinecone     | AssemblyAI    | S3         | gRPC |
