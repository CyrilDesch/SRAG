package com.cyrelis.srag.infrastructure.config

import com.cyrelis.srag.application.types.JobProcessingConfig
import zio.*

final case class RuntimeConfig(
  environment: String,
  api: ApiConfig,
  adapters: AdaptersConfig,
  migrations: MigrationConfig,
  fixtures: FixtureConfig,
  retry: RetryConfig,
  timeouts: TimeoutConfig,
  jobProcessing: JobProcessingConfig
)

final case class MigrationConfig(
  runOnStartup: Boolean,
  failOnError: Boolean
)

final case class FixtureConfig(
  loadOnStartup: Boolean
)

final case class RetryConfig(
  enabled: Boolean,
  maxRetries: Int,
  initialDelayMs: Long,
  maxDelayMs: Long,
  backoffFactor: Double
)

final case class TimeoutConfig(
  transcriptionMs: Long,
  embeddingMs: Long,
  databaseMs: Long,
  vectorStoreMs: Long,
  lexicalStoreMs: Long,
  rerankerMs: Long,
  blobStoreMs: Long,
  documentParserMs: Long
)

final case class ApiConfig(host: String, port: Int, maxBodySizeBytes: Long)

final case class AdaptersConfig(
  driven: DrivenAdaptersConfig,
  driving: DrivingAdaptersConfig
)

final case class DrivenAdaptersConfig(
  database: DatabaseAdapterConfig,
  vectorStore: VectorStoreAdapterConfig,
  lexicalStore: LexicalStoreAdapterConfig,
  reranker: RerankerAdapterConfig,
  transcriber: TranscriberAdapterConfig,
  embedder: EmbedderAdapterConfig,
  blobStore: BlobStoreAdapterConfig,
  jobQueue: JobQueueAdapterConfig
)

final case class DrivingAdaptersConfig(
  api: ApiAdapterConfig
)

enum DatabaseAdapterConfig:
  case Postgres(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String
  )

enum VectorStoreAdapterConfig:
  case Qdrant(url: String, apiKey: String, collection: String)

enum LexicalStoreAdapterConfig:
  case OpenSearch(url: String, index: String, username: Option[String], password: Option[String])

enum RerankerAdapterConfig:
  case Transformers(model: String, apiUrl: String)

enum TranscriberAdapterConfig:
  case Whisper(modelPath: String, apiUrl: String)
  case AssemblyAI(apiUrl: String, apiKey: String)

enum EmbedderAdapterConfig:
  case HuggingFace(model: String, apiUrl: String)

enum BlobStoreAdapterConfig:
  case MinIO(host: String, port: Int, accessKey: String, secretKey: String, bucket: String)

enum JobQueueAdapterConfig:
  case Redis(
    host: String,
    port: Int,
    database: Int,
    password: Option[String],
    queueKey: String,
    deadLetterKey: String
  )

enum ApiAdapterConfig:
  case REST(host: String, port: Int, maxBodySizeBytes: Long)
  case GRPC(host: String, port: Int)

object RuntimeConfig {

  val layer: ZLayer[Any, Throwable, RuntimeConfig] =
    ZLayer.fromZIO(ConfigLoader.load)
}
