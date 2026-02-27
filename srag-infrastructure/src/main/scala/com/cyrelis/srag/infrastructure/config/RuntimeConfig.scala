package com.cyrelis.srag.infrastructure.config

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

import com.cyrelis.srag.application.model.ingestion.JobProcessingConfig
import zio.*

final case class RuntimeConfig(
  api: ApiConfig,
  adapters: AdaptersConfig,
  migrations: MigrationConfig,
  fixtures: FixtureConfig,
  jobs: JobsConfig
) {
  def jobProcessing: JobProcessingConfig = JobProcessingConfig(
    maxAttempts = jobs.maxAttempts,
    pollInterval = FiniteDuration(jobs.pollIntervalMs, MILLISECONDS),
    batchSize = jobs.batchSize,
    initialRetryDelay = FiniteDuration(jobs.initialRetryDelayMs, MILLISECONDS),
    maxRetryDelay = FiniteDuration(jobs.maxRetryDelayMs, MILLISECONDS),
    backoffFactor = jobs.backoffFactor,
    maxConcurrentJobs = jobs.maxConcurrentJobs
  )
}

final case class RuntimeEnvConfig(environment: String)

final case class JobsConfig(
  maxAttempts: Int,
  pollIntervalMs: Long,
  batchSize: Int,
  initialRetryDelayMs: Long,
  maxRetryDelayMs: Long,
  backoffFactor: Double,
  maxConcurrentJobs: Int
)

final case class MigrationConfig(
  runOnStartup: Boolean
)

final case class FixtureConfig(
  loadOnStartup: Boolean
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
  case Postgres(host: String, port: Int, database: String, user: String, password: String)

enum VectorStoreAdapterConfig:
  case Qdrant(url: String, apiKey: Option[String], collection: String)

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
  case Redis(host: String, port: Int, database: Int, password: Option[String], queueKey: String, deadLetterKey: String)

enum ApiAdapterConfig:
  case REST(host: String, port: Int, maxBodySizeBytes: Long)

object RuntimeConfig {
  val layer: ZLayer[Any, Throwable, RuntimeConfig] =
    ZLayer.fromZIO(ConfigLoader.load)
}
