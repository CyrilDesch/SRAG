package com.cyrelis.srag.infrastructure.config

import com.typesafe.config.ConfigFactory
import zio.*
import zio.config.*
import zio.config.magnolia.deriveConfig
import zio.config.typesafe.TypesafeConfigProvider

object ConfigLoader {

  sealed trait ConfigurationError       extends Throwable with scala.util.control.NoStackTrace
  case class LoadingFailed(msg: String) extends ConfigurationError {
    override def getMessage: String = msg
  }

  def load: Task[RuntimeConfig] = ZIO
    .attempt(ConfigFactory.load())
    .flatMap { raw =>
      val provider = TypesafeConfigProvider.fromTypesafeConfig(raw)

      def loadCommon[A](config: Config[A], path: String*): Task[A] = {
        val nestedConfig = path.foldRight(config.toKebabCase)((p, c) => c.nested(p))
        provider.load(nestedConfig)
      }

      val loadDb = ZIO.suspendSucceed {
        val typ = raw.getString("srag.adapters.driven.database.type")
        typ match {
          case "postgres" =>
            loadCommon(deriveConfig[DatabaseAdapterConfig.Postgres], "srag", "adapters", "driven", "database", typ)
          case other => ZIO.fail(new Exception(s"Unknown database type: $other"))
        }
      }

      val loadVectorStore = ZIO.suspendSucceed {
        val typ = raw.getString("srag.adapters.driven.vector-store.type")
        typ match {
          case "qdrant" =>
            loadCommon(deriveConfig[VectorStoreAdapterConfig.Qdrant], "srag", "adapters", "driven", "vector-store", typ)
          case other => ZIO.fail(new Exception(s"Unknown vector-store type: $other"))
        }
      }

      val loadLexicalStore = ZIO.suspendSucceed {
        val typ = raw.getString("srag.adapters.driven.lexical-store.type")
        typ match {
          case "opensearch" =>
            loadCommon(
              deriveConfig[LexicalStoreAdapterConfig.OpenSearch],
              "srag",
              "adapters",
              "driven",
              "lexical-store",
              typ
            )
          case other => ZIO.fail(new Exception(s"Unknown lexical-store type: $other"))
        }
      }

      val loadReranker = ZIO.suspendSucceed {
        val typ = raw.getString("srag.adapters.driven.reranker.type")
        typ match {
          case "transformers" =>
            loadCommon(deriveConfig[RerankerAdapterConfig.Transformers], "srag", "adapters", "driven", "reranker", typ)
          case other => ZIO.fail(new Exception(s"Unknown reranker type: $other"))
        }
      }

      val loadTranscriber = ZIO.suspendSucceed {
        val typ = raw.getString("srag.adapters.driven.transcriber.type")
        typ match {
          case "whisper" =>
            loadCommon(deriveConfig[TranscriberAdapterConfig.Whisper], "srag", "adapters", "driven", "transcriber", typ)
          case "assemblyai" =>
            loadCommon(
              deriveConfig[TranscriberAdapterConfig.AssemblyAI],
              "srag",
              "adapters",
              "driven",
              "transcriber",
              typ
            )
          case other => ZIO.fail(new Exception(s"Unknown transcriber type: $other"))
        }
      }

      val loadEmbedder = ZIO.suspendSucceed {
        val typ = raw.getString("srag.adapters.driven.embedder.type")
        typ match {
          case "huggingface" =>
            loadCommon(deriveConfig[EmbedderAdapterConfig.HuggingFace], "srag", "adapters", "driven", "embedder", typ)
          case other => ZIO.fail(new Exception(s"Unknown embedder type: $other"))
        }
      }

      val loadBlobStore = ZIO.suspendSucceed {
        val typ = raw.getString("srag.adapters.driven.blob-store.type")
        typ match {
          case "minio" =>
            loadCommon(deriveConfig[BlobStoreAdapterConfig.MinIO], "srag", "adapters", "driven", "blob-store", typ)
          case other => ZIO.fail(new Exception(s"Unknown blob-store type: $other"))
        }
      }

      val loadJobQueue = ZIO.suspendSucceed {
        val typ = raw.getString("srag.adapters.driven.job-queue.type")
        typ match {
          case "redis" =>
            loadCommon(deriveConfig[JobQueueAdapterConfig.Redis], "srag", "adapters", "driven", "job-queue", typ)
          case other => ZIO.fail(new Exception(s"Unknown job-queue type: $other"))
        }
      }

      val loadDrivingApi = ZIO.suspendSucceed {
        val typ = raw.getString("srag.adapters.driving.api.type")
        typ match {
          case "rest" => loadCommon(deriveConfig[ApiAdapterConfig.REST], "srag", "adapters", "driving", "api", typ)
          case other  => ZIO.fail(new Exception(s"Unknown api adapter type: $other"))
        }
      }

      for {
        api          <- loadCommon(deriveConfig[ApiConfig], "srag", "api")
        migrations   <- loadCommon(deriveConfig[MigrationConfig], "srag", "migrations")
        fixtures     <- loadCommon(deriveConfig[FixtureConfig], "srag", "fixtures")
        jobs         <- loadCommon(deriveConfig[JobsConfig], "srag", "jobs")
        db           <- loadDb
        vectorStore  <- loadVectorStore
        lexicalStore <- loadLexicalStore
        reranker     <- loadReranker
        transcriber  <- loadTranscriber
        embedder     <- loadEmbedder
        blobStore    <- loadBlobStore
        jobQueue     <- loadJobQueue

        drivingApi <- loadDrivingApi
      } yield RuntimeConfig(
        api = api,
        adapters = AdaptersConfig(
          driven = DrivenAdaptersConfig(
            database = db,
            vectorStore = vectorStore,
            lexicalStore = lexicalStore,
            reranker = reranker,
            transcriber = transcriber,
            embedder = embedder,
            blobStore = blobStore,
            jobQueue = jobQueue
          ),
          driving = DrivingAdaptersConfig(api = drivingApi)
        ),
        migrations = migrations,
        fixtures = fixtures,
        jobs = jobs
      )
    }
    .catchAll { err =>
      val errors = err.toString
        .split("""\)\s+and\s+\(""")
        .map(_.replaceAll("""^[()]+|[()]+$""", ""))
        .toList

      ZIO.foreachDiscard(errors)(msg => ZIO.logError(s"Missing or invalid configuration: $msg")) *>
        ZIO.fail(LoadingFailed("Configuration loading failed. Check the logs above for missing parameters."))
    }
}
