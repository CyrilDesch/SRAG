# SRAG (still in development)

**An Open-source Scala-based Hybrid RAG offering deep document understanding and audio processing. Built with a flexible architecture that lets you easily plug in different models or storage systems, stateless and scalable by design.**

## Differences Against Other RAG Frameworks

- **RAGFlow**: We bring JVM-based hexagonal architecture with declarative swappable adapters and stateless horizontal scaling, while RAGFlow is Python-based with a built-in UI.
- **Haystack**: We bring type-safe Scala/ZIO specialized for RAG ingestion and hybrid retrieval, while Haystack is a general-purpose Python NLP framework. We don't offer general NLP pipelines, agent orchestration, or prompt management systems
- **txtAI**: We bring enterprise hexagonal architecture designed for distributed cloud deployments with swappable adapters, while txtAI is lightweight and monolithic for single-machine setups. We don't optimize for single-machine deployment or provide semantic workflow utilities

SRAG is designed with **Hexagonal Architecture** principles, making it easy to swap implementations without touching business logic. Built with **ZIO** for type-safe concurrency and **Scala 3** for modern functional programming.

## Features

- **Document ingestion** : Upload text and documents (PDF, DOCX, etc.) for processing, storage, vectorization, and direct access via RAG queries. Visual content (diagrams, tables, charts) is automatically detected and extracted using intelligent vision models
- **Audio ingestion** : Upload audio files, get them transcribed automatically, stored, vectorized, and directly accessible via RAG queries
- **Image ingestion** : Upload images, extract text content and describe visual elements (diagrams, tables, charts) using LLM vision models, stored, vectorized, and directly accessible via RAG queries
- **Metadata support** : Attach custom metadata (e.g., userId, projectId, tags) during ingestion for user-specific access control and filtering in RAG queries
- **MCP server** : Model Context Protocol server for seamless integration with AI assistants
- **RAG query** : Hybrid retrieval combining vector search, lexical search (BM25), and reranking with metadata filtering
- **Declarative configuration** : Swap models, storage, and components through config files
- **Resilient and stateless** : Retry mechanisms, timeouts, and horizontal scaling support

## Roadmap

- [x] Implement a Gateway (REST for now)
- [x] Implement a Transcriber Adapter
- [x] Allow audio ingestion
- [x] Implement a Database Adapter
- [x] Implement an Embedder Adapter
- [x] Implement a Vector Store Adapter
- [x] Implement a Blob Store Adapter
- [x] Implement a Job Queue Adapter
- [ ] Allow text and documents (pdf, docx, ...) ingestion
- [ ] Implement Deep Document Understanding
- [ ] Implement MCP server
- [ ] Implement image to text
- [ ] Implement gRPC Gateway
- [x] Implement Retry and Timeout Services
- [x] Implement Reranker
- [x] Implement Lexical Search (BM25)
- [ ] Add daily cleanup job: delete orphan blobs and stale DB jobs (>24h)
- [ ] Migrations should use a datasource instead of hardcoded
- [ ] Implement better test of infrastructure modules
- [ ] Implement test of application use cases
- [ ] Build a documentation website
- [ ] Handle removing of documents, audio or text in all our structures
- [ ] Handle updating of documents, audio or text in all our structures

## 📋 Prerequisites

- **JDK 21+**
- **sbt 1.10+**
- **Docker & Docker Compose**

Note : No API keys or third-party accounts needed for the default configuration!

## 🚀 Quick Start

### 1. Clone the repository

```bash
git clone https://github.com/CyrilDesch/srag.git
cd srag
```

### 2. Start all services with Docker Compose

```bash
# Start all open-source dependencies
docker compose up -d
```

This starts the following Docker containers:

- **PostgreSQL** (database) on `localhost:5432`
- **Qdrant** (vector store) on `localhost:6333`
- **Whisper** (transcription with faster-whisper) on `localhost:9001`
- **Text Embeddings Inference** (HuggingFace embeddings) on `localhost:8082`
- **MinIO** (S3-compatible storage) on `localhost:9000`
- **Redis** (persistent job queue) on `localhost:6379`

All services are ready to use with **no configuration needed**. The default `application.conf` is pre-configured to use these services.

### 3. Compile the project

```bash
sbt compile
```

### 4. Run the application

```bash
sbt "srag-infrastructure/run"
```

## 🎯 Usage

### Running the application

```bash
# Standard run (make sure Docker services are running first)
sbt "srag-infrastructure/run"

# Hot reload on code changes (development)
sbt "~srag-infrastructure/reStart"

# Test the application
sbt test
```

### Visual Content Extraction

SRAG intelligently extracts visual content from documents and images, ensuring diagrams, tables, charts, and other visual elements are properly indexed and searchable.

**How it works:**

1. **Text extraction first**: Documents are processed using standard text extraction tools (PDFBox, POI) to extract text content efficiently
2. **Smart detection**: The system automatically detects pages or sections with visual elements (low text-to-image ratio, detected visual structures)
3. **Intelligent extraction**: When visual content is detected, LLM vision models are used to:
   - Extract text from images and scanned documents
   - Describe diagrams, flowcharts, and complex visual structures in natural language
   - Convert tables into structured text format (markdown or formatted text)
   - Analyze charts and graphs to extract data and insights

**Benefits:**

- **Cost-effective**: Only uses expensive vision models when visual content is actually detected, keeping costs low for text-heavy documents
- **Comprehensive**: Captures both textual and visual information, ensuring nothing is missed
- **Searchable**: All visual content is converted to searchable text descriptions, making diagrams and tables queryable via RAG
- **Configurable**: Choose between open-source vision models (cost-effective) or commercial models (maximum accuracy) based on your needs

**What gets extracted:**

- ✅ Text from images and scanned documents
- ✅ Tables (converted to structured format)
- ✅ Diagrams and flowcharts (described in natural language)
- ✅ Charts and graphs (data and insights extracted)
- ✅ Complex layouts (properly interpreted and structured)

## 🏗️ Architecture

SRAG follows **Hexagonal Architecture** (Ports & Adapters) with three distinct modules:

![Hexagonal Architecture Schema](docs/hexa-schema/image.png)

### `srag-domain`

**Pure business logic** with zero external dependencies.

### `srag-application`

**Use cases and port definitions** orchestrating business workflows.

### `srag-infrastructure`

**Concrete implementations** of all adapters and runtime concerns.

### Asynchronous Ingestion Pattern (Queue Port + Worker)

- The API persists the job via the Job Repository Port, then enqueues the job ID via the Job Queue Port.
- A background Worker consumes from the Job Queue Port and orchestrates processing through the Blob Store Port, Transcriber Port, Embedder Port, Database Port, and Vector Store Port.
- If enqueue fails, the API request fails (client can retry). No inline cleanup is performed.
- Operational jobs :
  - Daily cleanup: delete orphan blobs and remove stale jobs older than 24h via the relevant ports.

## ⚙️ Configuration

SRAG uses **declarative configuration** through `application.conf`. Change adapters without modifying code!

### Example: Switching Database

```hocon
# PostgreSQL configuration (default)
srag.adapters.driven.database {
  type = "postgres"
  postgres {
    host = "localhost"
    port = 5432
    database = "srag"
    user = "srag"
    password = "srag"
  }
}
```

### Available Adapter Types

| Component            | Implementation    | Default (Docker)     |
| -------------------- | ----------------- | -------------------- |
| **Database**         | PostgreSQL        | PostgreSQL           |
| **Vector Store**     | Qdrant            | Qdrant               |
| **Transcriber**      | Whisper           | Whisper              |
| **Embedder**         | HuggingFace       | HuggingFace          |
| **Vision Extractor** | LLM Vision Models | LLaVA / GPT-4 Vision |
| **Blob Store**       | MinIO             | MinIO                |
| **Job Queue**        | Redis             | Redis                |
| **API Gateway**      | REST / gRPC       | REST                 |

### Vision Model Configuration

SRAG supports multiple vision models for visual content extraction. Configure your preferred model in `application.conf`:

```hocon
srag.adapters.driven.vision {
  type = "llava"  # Options: "llava" (open-source, cost-effective), "gpt4-vision" (commercial, high accuracy), "qwen-vl" (open-source alternative)

  llava {
    api-url = "http://localhost:8083"
  }

  gpt4-vision {
    api-key = ${?OPENAI_API_KEY}
    model = "gpt-4-vision-preview"
  }

  qwen-vl {
    api-url = "http://localhost:8084"
  }
}
```

**Model Recommendations:**

- **Cost-effective**: Use `llava` or `qwen-vl` (open-source, self-hosted) for most use cases
- **Maximum accuracy**: Use `gpt4-vision` for critical documents requiring the highest extraction quality
- **Hybrid approach**: Configure fallback behavior - use open-source models first, fall back to GPT-4 Vision for complex cases

## Environment Variables

The default configuration requires **no environment variables**. For production, you can override settings:

```bash
# Optional: Override database password
export DB_PASSWORD=secret123

# Optional: Override service URLs
export QDRANT_URL=http://your-qdrant:6333
export WHISPER_URL=http://your-whisper:9001
export HUGGINGFACE_URL=http://your-embeddings:8082
export MINIO_ENDPOINT=http://your-minio:9000
export REDIS_HOST=your-redis-host
export REDIS_PORT=6379
export REDIS_PASSWORD=secret

sbt "srag-infrastructure/run"
```

Reference them in `application.conf`:

```hocon
srag.adapters.driven.database {
  postgres {
    password = ${?DB_PASSWORD}  # Optional override
    host = ${?DB_HOST}          # Optional override
  }
}
```

## Extending SRAG

### Adding a Driven Adapter

To add a new driven adapter (e.g., Redis, MongoDB, etc.):

1. Define the port interface in `srag-application/***/ports/driven/` (if it doesn't exist)
2. Add config types in `RuntimeConfig.scala`
3. Implement the adapter in `srag-infrastructure/***/adapters/driven/[type]/[tech]/`
4. Add factory case in `AdapterFactory.scala`
5. Add parser logic in `ConfigLoader.scala`
6. Document it in `application.conf`
7. (Optional) Add Docker service to `docker-compose.yml`

### Adding a Driving Adapter

To add a new driving adapter (e.g., WebSocket, CLI, etc.):

1. Define the driving port in `srag-application/***/ports/driving/`
2. Add config types in `RuntimeConfig.scala`
3. Implement the adapter in `srag-infrastructure/***/adapters/driving/[tech]/`
4. Wire it in the runtime module with ZIO layers
5. Update `application.conf` with the new adapter configuration

### Development Workflow

1. Model domain entities in `srag-domain`
2. Define ports in `srag-application/***/ports`
3. Implement use cases in `srag-application/***/usecase`
4. Create adapters in `srag-infrastructure/***/adapters`
5. Wire everything in `srag-infrastructure/***/runtime`
6. Configure in `application.conf`

## 🧪 Testing

```bash
# Run all tests
sbt test

# Run tests for a specific module
sbt srag-domain/test
sbt srag-application/test
sbt srag-infrastructure/test

# Run tests with coverage
sbt coverage test coverageReport
```

## 📚 Documentation

- **Architecture**: Hexagonal architecture principles and module structure (see Architecture section above)
- **Configuration**: Declarative adapter configuration system (see Configuration section above)
- **Cursor Rules**: `.cursor/rules/` - AI-assisted development guidelines

## 🤝 Contributing

We welcome contributions! This is an open-source project following strict architectural principles.

### Guidelines

- ✅ Follow hexagonal architecture boundaries
- ✅ Keep domain module pure (no external dependencies)
- ✅ Add tests for new features
- ✅ Fix all compiler and linter warnings
- ✅ Update configuration documentation when adding adapters
- ✅ Write clear commit messages

### Code Quality Standards

- Zero compiler warnings
- Zero linter warnings
- Explicit type signatures for all public APIs
- Immutable domain types
- Typed errors (no raw exceptions)
- HOCON configuration with type-safe parsing

## 📄 License

[GNU General Public License v3.0](LICENSE)

## 🙏 Acknowledgments

Built with:

- [Scala 3](https://www.scala-lang.org/) - Modern functional programming
- [ZIO](https://zio.dev/) - Type-safe, composable concurrency
- [gRPC](https://grpc.io/) - High-performance RPC framework
