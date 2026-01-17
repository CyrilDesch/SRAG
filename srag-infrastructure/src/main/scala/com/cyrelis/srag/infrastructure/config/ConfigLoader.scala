package com.cyrelis.srag.infrastructure.config

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

import com.cyrelis.srag.application.types.JobProcessingConfig
import com.typesafe.config.{Config, ConfigFactory}
import zio.*

object ConfigLoader {

  def load: Task[RuntimeConfig] = ZIO.attempt {
    val config = ConfigFactory.load().getConfig("srag")

    RuntimeConfig(
      environment = config.getString("runtime.environment"),
      api = loadApiConfig(config),
      adapters = loadAdaptersConfig(config),
      migrations = loadMigrationConfig(config),
      fixtures = loadFixtureConfig(config),
      retry = loadRetryConfig(config),
      timeouts = loadTimeoutConfig(config),
      jobProcessing = loadJobProcessingConfig(config)
    )
  }

  private def loadMigrationConfig(config: Config): MigrationConfig =
    MigrationConfig(
      runOnStartup = if (config.hasPath("migrations.run-on-startup")) {
        config.getBoolean("migrations.run-on-startup")
      } else true,
      failOnError = if (config.hasPath("migrations.fail-on-error")) {
        config.getBoolean("migrations.fail-on-error")
      } else true
    )

  private def loadFixtureConfig(config: Config): FixtureConfig =
    FixtureConfig(
      loadOnStartup = if (config.hasPath("fixtures.load-on-startup")) {
        config.getBoolean("fixtures.load-on-startup")
      } else false
    )

  private def loadRetryConfig(config: Config): RetryConfig = {
    val retryPath = "retry"
    if (config.hasPath(retryPath)) {
      val retryConfig = config.getConfig(retryPath)
      RetryConfig(
        enabled = if (retryConfig.hasPath("enabled")) {
          retryConfig.getBoolean("enabled")
        } else true,
        maxRetries = if (retryConfig.hasPath("max-retries")) {
          retryConfig.getInt("max-retries")
        } else 2,
        initialDelayMs = if (retryConfig.hasPath("initial-delay-ms")) {
          retryConfig.getLong("initial-delay-ms")
        } else 100,
        maxDelayMs = if (retryConfig.hasPath("max-delay-ms")) {
          retryConfig.getLong("max-delay-ms")
        } else 5000,
        backoffFactor = if (retryConfig.hasPath("backoff-factor")) {
          retryConfig.getDouble("backoff-factor")
        } else 2.0
      )
    } else {
      RetryConfig(
        enabled = true,
        maxRetries = 2,
        initialDelayMs = 100,
        maxDelayMs = 5000,
        backoffFactor = 2.0
      )
    }
  }

  private def loadTimeoutConfig(config: Config): TimeoutConfig = {
    val timeoutPath = "timeouts"
    if (config.hasPath(timeoutPath)) {
      val timeoutConfig = config.getConfig(timeoutPath)
      TimeoutConfig(
        transcriptionMs = if (timeoutConfig.hasPath("transcription-ms")) {
          timeoutConfig.getLong("transcription-ms")
        } else 300000,
        embeddingMs = if (timeoutConfig.hasPath("embedding-ms")) {
          timeoutConfig.getLong("embedding-ms")
        } else 30000,
        databaseMs = if (timeoutConfig.hasPath("database-ms")) {
          timeoutConfig.getLong("database-ms")
        } else 5000,
        vectorStoreMs = if (timeoutConfig.hasPath("vector-store-ms")) {
          timeoutConfig.getLong("vector-store-ms")
        } else 10000,
        lexicalStoreMs = if (timeoutConfig.hasPath("lexical-store-ms")) {
          timeoutConfig.getLong("lexical-store-ms")
        } else 10000,
        rerankerMs = if (timeoutConfig.hasPath("reranker-ms")) {
          timeoutConfig.getLong("reranker-ms")
        } else 15000,
        blobStoreMs = if (timeoutConfig.hasPath("blob-store-ms")) {
          timeoutConfig.getLong("blob-store-ms")
        } else 15000,
        documentParserMs = if (timeoutConfig.hasPath("document-parser-ms")) {
          timeoutConfig.getLong("document-parser-ms")
        } else 60000
      )
    } else {
      TimeoutConfig(
        transcriptionMs = 300000,
        embeddingMs = 30000,
        databaseMs = 5000,
        vectorStoreMs = 10000,
        lexicalStoreMs = 10000,
        rerankerMs = 15000,
        blobStoreMs = 15000,
        documentParserMs = 60000
      )
    }
  }

  private def loadJobProcessingConfig(config: Config): JobProcessingConfig = {
    val jobPath = "jobs"

    if (config.hasPath(jobPath)) {
      val jobConfig = config.getConfig(jobPath)
      JobProcessingConfig(
        maxAttempts = if (jobConfig.hasPath("max-attempts")) jobConfig.getInt("max-attempts") else 3,
        pollInterval = FiniteDuration(
          if (jobConfig.hasPath("poll-interval-ms")) jobConfig.getLong("poll-interval-ms") else 1000L,
          MILLISECONDS
        ),
        batchSize = if (jobConfig.hasPath("batch-size")) jobConfig.getInt("batch-size") else 5,
        initialRetryDelay = FiniteDuration(
          if (jobConfig.hasPath("initial-retry-delay-ms")) jobConfig.getLong("initial-retry-delay-ms") else 2000L,
          MILLISECONDS
        ),
        maxRetryDelay = FiniteDuration(
          if (jobConfig.hasPath("max-retry-delay-ms")) jobConfig.getLong("max-retry-delay-ms") else 60000L,
          MILLISECONDS
        ),
        backoffFactor = if (jobConfig.hasPath("backoff-factor")) jobConfig.getDouble("backoff-factor") else 2.0,
        maxConcurrentJobs = if (jobConfig.hasPath("max-concurrent-jobs")) jobConfig.getInt("max-concurrent-jobs") else 1
      )
    } else {
      JobProcessingConfig(
        maxAttempts = 3,
        pollInterval = FiniteDuration(1000L, MILLISECONDS),
        batchSize = 5,
        initialRetryDelay = FiniteDuration(2000L, MILLISECONDS),
        maxRetryDelay = FiniteDuration(60000L, MILLISECONDS),
        backoffFactor = 2.0,
        maxConcurrentJobs = 1
      )
    }
  }

  private def loadApiConfig(config: Config): ApiConfig =
    ApiConfig(
      host = config.getString("api.host"),
      port = config.getInt("api.port"),
      maxBodySizeBytes = if (config.hasPath("api.max-body-size-bytes")) {
        config.getLong("api.max-body-size-bytes")
      } else 104857600L // Default 100MB
    )

  private def loadAdaptersConfig(config: Config): AdaptersConfig =
    AdaptersConfig(
      driven = loadDrivenAdaptersConfig(config.getConfig("adapters.driven")),
      driving = loadDrivingAdaptersConfig(config.getConfig("adapters.driving"))
    )

  private def loadDrivenAdaptersConfig(config: Config): DrivenAdaptersConfig =
    DrivenAdaptersConfig(
      database = loadDatabaseConfig(config.getConfig("database")),
      vectorStore = loadVectorStoreConfig(config.getConfig("vector-store")),
      lexicalStore = loadLexicalStoreConfig(config.getConfig("lexical-store")),
      reranker = loadRerankerConfig(config.getConfig("reranker")),
      transcriber = loadTranscriberConfig(config.getConfig("transcriber")),
      embedder = loadEmbedderConfig(config.getConfig("embedder")),
      blobStore = loadBlobStoreConfig(config.getConfig("blob-store")),
      jobQueue = loadJobQueueConfig(config.getConfig("job-queue"))
    )

  private def loadDatabaseConfig(config: Config): DatabaseAdapterConfig = {
    val dbType = config.getString("type")
    dbType match {
      case "postgres" =>
        val pgConfig = config.getConfig("postgres")
        DatabaseAdapterConfig.Postgres(
          host = pgConfig.getString("host"),
          port = pgConfig.getInt("port"),
          database = pgConfig.getString("database"),
          user = pgConfig.getString("user"),
          password = pgConfig.getString("password")
        )

      case other =>
        throw new IllegalArgumentException(s"Unknown database type: $other")
    }
  }

  private def loadRerankerConfig(config: Config): RerankerAdapterConfig = {
    val rType = config.getString("type")
    rType match {
      case "transformers" =>
        val trConfig = config.getConfig("transformers")
        RerankerAdapterConfig.Transformers(
          model = trConfig.getString("model"),
          apiUrl = trConfig.getString("api-url")
        )
      case other =>
        throw new IllegalArgumentException(s"Unknown reranker type: $other")
    }
  }

  private def loadVectorStoreConfig(config: Config): VectorStoreAdapterConfig = {
    val vsType = config.getString("type")
    vsType match {
      case "qdrant" =>
        val qdConfig = config.getConfig("qdrant")
        VectorStoreAdapterConfig.Qdrant(
          url = qdConfig.getString("url"),
          apiKey =
            if (qdConfig.hasPath("api-key") && !qdConfig.getString("api-key").isEmpty)
              qdConfig.getString("api-key")
            else "",
          collection = qdConfig.getString("collection")
        )

      case other =>
        throw new IllegalArgumentException(s"Unknown vector-store type: $other")
    }
  }

  private def loadLexicalStoreConfig(config: Config): LexicalStoreAdapterConfig = {
    val lsType = config.getString("type")
    lsType match {
      case "opensearch" =>
        val osConfig = config.getConfig("opensearch")
        LexicalStoreAdapterConfig.OpenSearch(
          url = osConfig.getString("url"),
          index = osConfig.getString("index"),
          username =
            if (osConfig.hasPath("username") && !osConfig.getString("username").isEmpty)
              Some(osConfig.getString("username"))
            else None,
          password =
            if (osConfig.hasPath("password") && !osConfig.getString("password").isEmpty)
              Some(osConfig.getString("password"))
            else None
        )
      case other =>
        throw new IllegalArgumentException(s"Unknown lexical-store type: $other")
    }
  }

  private def loadTranscriberConfig(config: Config): TranscriberAdapterConfig = {
    val tType = config.getString("type")
    tType match {
      case "whisper" =>
        val whConfig = config.getConfig("whisper")
        TranscriberAdapterConfig.Whisper(
          modelPath = whConfig.getString("model-path"),
          apiUrl = whConfig.getString("api-url")
        )
      case "assemblyai" =>
        val aaiConfig = config.getConfig("assemblyai")
        TranscriberAdapterConfig.AssemblyAI(
          apiUrl = aaiConfig.getString("api-url"),
          apiKey = aaiConfig.getString("api-key")
        )

      case other =>
        throw new IllegalArgumentException(s"Unknown transcriber type: $other")
    }
  }

  private def loadEmbedderConfig(config: Config): EmbedderAdapterConfig = {
    val eType = config.getString("type")
    eType match {
      case "huggingface" =>
        val hfConfig = config.getConfig("huggingface")
        EmbedderAdapterConfig.HuggingFace(
          model = hfConfig.getString("model"),
          apiUrl = hfConfig.getString("api-url")
        )

      case other =>
        throw new IllegalArgumentException(s"Unknown embedder type: $other")
    }
  }

  private def loadBlobStoreConfig(config: Config): BlobStoreAdapterConfig = {
    val bType = config.getString("type")
    bType match {
      case "minio" =>
        val minioConfig = config.getConfig("minio")
        BlobStoreAdapterConfig.MinIO(
          host = minioConfig.getString("host"),
          port = minioConfig.getInt("port"),
          accessKey = minioConfig.getString("access-key"),
          secretKey = minioConfig.getString("secret-key"),
          bucket = minioConfig.getString("bucket")
        )

      case other =>
        throw new IllegalArgumentException(s"Unknown blob-store type: $other")
    }
  }

  private def loadJobQueueConfig(config: Config): JobQueueAdapterConfig = {
    val qType = config.getString("type")
    qType match {
      case "redis" =>
        val rConfig = config.getConfig("redis")
        JobQueueAdapterConfig.Redis(
          host = rConfig.getString("host"),
          port = rConfig.getInt("port"),
          database = if (rConfig.hasPath("database")) rConfig.getInt("database") else 0,
          password =
            if (rConfig.hasPath("password") && rConfig.getString("password").nonEmpty)
              Some(rConfig.getString("password"))
            else None,
          queueKey = if (rConfig.hasPath("queue-key")) rConfig.getString("queue-key") else "srag:jobs:queue",
          deadLetterKey =
            if (rConfig.hasPath("dead-letter-key")) rConfig.getString("dead-letter-key") else "srag:jobs:dead"
        )
      case other =>
        throw new IllegalArgumentException(s"Unknown job-queue type: $other")
    }
  }

  private def loadDrivingAdaptersConfig(config: Config): DrivingAdaptersConfig =
    DrivingAdaptersConfig(
      api = loadApiAdapterConfig(config.getConfig("api"))
    )

  private def loadApiAdapterConfig(config: Config): ApiAdapterConfig = {
    val apiType = config.getString("type")
    apiType match {
      case "rest" =>
        val restConfig = config.getConfig("rest")
        ApiAdapterConfig.REST(
          host = restConfig.getString("host"),
          port = restConfig.getInt("port"),
          maxBodySizeBytes = if (restConfig.hasPath("max-body-size-bytes")) {
            restConfig.getLong("max-body-size-bytes")
          } else 104857600L // Default 100MB
        )
      case "grpc" =>
        val grpcConfig = config.getConfig("grpc")
        ApiAdapterConfig.GRPC(
          host = grpcConfig.getString("host"),
          port = grpcConfig.getInt("port")
        )

      case other =>
        throw new IllegalArgumentException(s"Unknown api type: $other")
    }
  }
}
