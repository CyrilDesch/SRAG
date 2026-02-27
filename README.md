# SRAG (in development)

**SRAG is a scalable open-source RAG system with advanced document parsing and audio processing. It can be adapted to different infrastructures through environment variables, allowing you to plug it into different environments without code changes.**

## Key differences

- RAGFlow: a full product with a built-in UI and a managed stack, designed to be used as a standalone application. SRAG is a **microservice** you integrate into **your own stack**. Every component is **configurable** via environment variables so it fits your infrastructure **without touching the code**.
- txtAI: a Python library built for single-machine semantic workflows and local experimentation. SRAG is built for multi-instance, **stateless horizontal scaling**, making it a better fit for **production** workloads that need to grow.

## Features

- **RAG query**: Hybrid retrieval combining vector search, lexical search (BM25), and reranking with metadata filtering.
- **Document ingestion (in dev)**: Upload text and documents (PDF, DOCX, etc.) for processing, storage, vectorization, and direct access via RAG queries. Visual content (diagrams, tables, charts) is automatically detected and extracted using intelligent vision models.
- **Audio ingestion**: Upload audio files, get them transcribed automatically, stored, vectorized, and directly accessible via RAG queries.
- **Image ingestion (in dev)**: Upload images, extract text content and describe visual elements (diagrams, tables, charts) using LLM vision models, stored, vectorized, and directly accessible via RAG queries.
- **Dynamic configuration**: Configure components used via environment variables at runtime.
- **Metadata support**: Attach custom metadata (e.g., userId, projectId, tags) during ingestion for user-specific access control and filtering in RAG queries.
- **MCP server (in dev)**: Model Context Protocol server for seamless integration with AI assistants.
- **Scalable**: Built for horizontal scaling.

## 📋 Prerequisites

- **JDK 21+**
- **sbt 1.10+**
- **Docker & Docker Compose**

Note: No API keys or third-party accounts needed for the default configuration.

## 🚀 Quick Start

### 1. Clone the repository

```bash
git clone https://github.com/CyrilDesch/srag.git
cd srag
```

### 2. Start all services with Docker Compose

> **Note:** Docker images require ~30GB of free space on Linux, and ~15GB on macOS.

```bash
docker compose up -d
```

This starts the following Docker containers:

- **PostgreSQL** (database) on `localhost:5432`
- **Qdrant** (vector store) on `localhost:6333`
- **OpenSearch** (lexical search / BM25) on `localhost:9200`
- **Whisper** (transcription with faster-whisper) on `localhost:9001`
- **Text Embeddings Inference** (HuggingFace embeddings) on `localhost:8082`
- **Reranker** (cross-encoder reranker) on `localhost:8083`
- **MinIO** (S3-compatible storage) on `localhost:9000`
- **Redis** (persistent job queue) on `localhost:6379`

All services are ready to use with **no configuration needed**.

### 3. Compile the project

```bash
sbt compile
```

### 4. Copy the .env.example file to .env

```bash
cp .env.example .env
```

### 5. Run the project 

#### Recommended

```bash
# Standard run (make sure Docker services are running first)
./scripts/serverRun.sh

# Hot reload on code changes (development)
./scripts/hotServerRun.sh
```

#### Manually

**Load environment variables if needed**

```bash
source scripts/loadenv.sh
``` 

**Run the application**

```bash
# Standard run (make sure Docker services are running first)
sbt "srag-infrastructure/run"

# Hot reload on code changes (development)
sbt "~srag-infrastructure/reStart"
```

## 🏗️ Architecture

SRAG follows **Hexagonal Architecture** (Ports & Adapters) with three distinct modules, making it easy to add new implementations without touching business logic. Built with **ZIO** for type-safe concurrency and **Scala 3** for modern functional programming.

![Hexagonal Architecture Schema](docs/hexa-schema/image.png)

### `srag-domain`

**Pure business logic** with zero external dependencies.

### `srag-application`

**Use cases and port definitions** orchestrating business workflows.

### `srag-infrastructure`

**Concrete implementations** of all adapters and runtime concerns.

## ⚙️ Configuration

SRAG uses **declarative configuration** through `application.conf`. They have default values and can be overridden using environment variables.

The default configuration requires **no environment variables**. For production, you can override settings using environment variables. The documentation is in `.env.example`.

## 🛠️ Extending SRAG

### Adding a Driven Adapter

To add a new driven adapter (e.g., MongoDB, Pinecone, etc.):

1. Define the port interface in `srag-application/ports/` (if it doesn't exist)
2. Add the config type in `RuntimeConfig.scala`
3. Implement the adapter in `srag-infrastructure/adapters/driven/[type]/[tech]/`
4. Add the factory case in `AdapterFactory.scala`
5. Add the parser case in `ConfigLoader.scala`
6. Document it in `application.conf` and `.env.example`
7. (Optional) Add a Docker service to `docker-compose.yml`

### Adding a Driving Adapter

To add a new driving adapter (e.g., WebSocket, CLI, etc.):

1. Consume the relevant use case interface from `srag-application/usecases/`
2. Add the config type in `RuntimeConfig.scala`
3. Implement the adapter in `srag-infrastructure/adapters/driving/[tech]/`
4. Wire it in `srag-infrastructure/runtime/` with ZIO layers
5. Add the parser case in `ConfigLoader.scala`
6. Document it in `application.conf` and `.env.example`

## 🧪 Testing

```bash
# Run all tests
sbt test

# Run tests for a specific module
sbt srag-domain/test
sbt srag-application/test
sbt srag-infrastructure/test
```

## 🦾 LLM Usage

You can use an LLM to generate code/documentation **that you will review**. The rules are in the `.llmrules/` folder. You can duplicate it to adapt the rules for Claude Code, Cursor, etc.

## 📝 Roadmap

- [x] Implement a Gateway (REST for now)
- [x] Implement a Transcriber Adapter
- [x] Allow audio ingestion
- [x] Allow text ingestion
- [x] Implement a Database Adapter
- [x] Implement an Embedder Adapter
- [x] Implement a Vector Store Adapter
- [x] Implement a Blob Store Adapter
- [x] Implement a Job Queue Adapter
- [x] Implement Reranker
- [x] Implement Lexical Search (BM25)
- [ ] Allow documents (pdf, docx, ...) ingestion
- [ ] Implement Deep Document Understanding
- [ ] Add daily cleanup job: delete orphan blobs and stale DB jobs (>24h)
- [ ] Implement authentication
- [ ] Handle removing of documents, audio or text in all our structures
- [ ] Handle updating of documents, audio or text in all our structures
- [ ] Build a documentation website
- [ ] Implement gRPC Gateway
- [ ] Implement MCP server

## 🤝 Contributing

We welcome contributions! This is an open-source project following strict architectural principles.

### Guidelines

- Follow hexagonal architecture boundaries.
- Keep domain module pure (no external dependencies).
- Add tests for new application layer features.
- Fix all compiler and linter warnings.
- Update configuration documentation when adding adapters.
- Write clear commit messages.

### Code Quality Standards

- Zero compiler warnings.
- Zero linter warnings.
- Explicit type signatures for all public APIs.
- Immutable domain types.
- Typed errors (no raw exceptions).
- HOCON configuration with type-safe parsing.

## 📄 License

[GNU General Public License v3.0](LICENSE)

## 🙏 Acknowledgments

Built with:

- [Scala 3](https://www.scala-lang.org/) - Modern functional programming.
- [ZIO](https://zio.dev/) - Type-safe, composable concurrency.
