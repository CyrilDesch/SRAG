package com.cyrelis.srag.infrastructure.config

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.ports.driven.datasource.DatasourcePort
import com.cyrelis.srag.application.ports.driven.embedding.EmbedderPort
import com.cyrelis.srag.application.ports.driven.job.JobQueuePort
import com.cyrelis.srag.application.ports.driven.parser.DocumentParserPort
import com.cyrelis.srag.application.ports.driven.reranker.RerankerPort
import com.cyrelis.srag.application.ports.driven.storage.{BlobStorePort, LexicalStorePort, VectorStorePort}
import com.cyrelis.srag.application.ports.driven.transcription.TranscriberPort
import com.cyrelis.srag.domain.ingestionjob.IngestionJobRepository
import com.cyrelis.srag.domain.transcript.TranscriptRepository
import com.cyrelis.srag.infrastructure.adapters.driven.blobstore.MinioAdapter
import com.cyrelis.srag.infrastructure.adapters.driven.database.postgres.{
  PostgresDatasource,
  PostgresJobRepository,
  PostgresTranscriptRepository
}
import com.cyrelis.srag.infrastructure.adapters.driven.documentparser.PdfBoxParser
import com.cyrelis.srag.infrastructure.adapters.driven.embedder.HuggingFaceAdapter
import com.cyrelis.srag.infrastructure.adapters.driven.lexicalstore.OpenSearchAdapter
import com.cyrelis.srag.infrastructure.adapters.driven.queue.RedisJobQueueAdapter
import com.cyrelis.srag.infrastructure.adapters.driven.reranker.TransformersRerankerAdapter
import com.cyrelis.srag.infrastructure.adapters.driven.transcriber.{AssemblyAIAdapter, WhisperAdapter}
import com.cyrelis.srag.infrastructure.adapters.driven.vectorstore.QdrantAdapter
import com.cyrelis.srag.infrastructure.adapters.driving.Gateway
import com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.IngestRestGateway
import zio.*

object AdapterFactory {

  def createDatasourceLayer(config: DatabaseAdapterConfig): ZLayer[Any, Throwable, DatasourcePort] =
    config match {
      case cfg: DatabaseAdapterConfig.Postgres =>
        PostgresDatasource.layer(cfg).map(ds => ZEnvironment(ds.get: DatasourcePort))
    }

  def createTranscriptRepositoryLayer(
    config: DatabaseAdapterConfig
  ): ZLayer[DatasourcePort, Nothing, TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]]] =
    config match {
      case _: DatabaseAdapterConfig.Postgres =>
        ZLayer.fromFunction { (datasource: DatasourcePort) =>
          val quillDatasource = datasource.asInstanceOf[PostgresDatasource]
          new PostgresTranscriptRepository(quillDatasource.quillContext)
        }
    }

  def createJobRepositoryLayer(
    config: DatabaseAdapterConfig
  ): ZLayer[DatasourcePort, Nothing, IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]]] =
    config match {
      case _: DatabaseAdapterConfig.Postgres =>
        ZLayer.fromFunction { (datasource: DatasourcePort) =>
          val quillDatasource = datasource.asInstanceOf[PostgresDatasource]
          new PostgresJobRepository(quillDatasource.quillContext)
        }
    }

  def createVectorStoreAdapter(config: VectorStoreAdapterConfig): VectorStorePort =
    config match {
      case cfg: VectorStoreAdapterConfig.Qdrant =>
        new QdrantAdapter(cfg)
    }

  def createLexicalStoreAdapter(config: LexicalStoreAdapterConfig): LexicalStorePort =
    config match {
      case cfg: LexicalStoreAdapterConfig.OpenSearch =>
        new OpenSearchAdapter(cfg)
    }

  def createRerankerAdapter(config: RerankerAdapterConfig): RerankerPort =
    config match {
      case cfg: RerankerAdapterConfig.Transformers =>
        new TransformersRerankerAdapter(cfg)
    }

  def createTranscriberAdapter(config: TranscriberAdapterConfig): TranscriberPort =
    config match {
      case cfg: TranscriberAdapterConfig.Whisper =>
        new WhisperAdapter(cfg)
      case cfg: TranscriberAdapterConfig.AssemblyAI =>
        new AssemblyAIAdapter(cfg)
    }

  def createEmbedderAdapter(config: EmbedderAdapterConfig): EmbedderPort =
    config match {
      case cfg: EmbedderAdapterConfig.HuggingFace =>
        new HuggingFaceAdapter(cfg)
    }

  def createBlobStoreAdapter(config: BlobStoreAdapterConfig): BlobStorePort =
    config match {
      case cfg: BlobStoreAdapterConfig.MinIO =>
        new MinioAdapter(cfg.host, cfg.port, cfg.accessKey, cfg.secretKey, cfg.bucket)
    }

  def createDocumentParserAdapter(): DocumentParserPort =
    new PdfBoxParser()

  def createJobQueueLayer(config: JobQueueAdapterConfig): ZLayer[Any, Throwable, JobQueuePort] =
    config match {
      case cfg: JobQueueAdapterConfig.Redis =>
        RedisJobQueueAdapter.layerFromConfig(cfg)
    }

  def createGateway(config: ApiAdapterConfig): Gateway =
    config match {
      case ApiAdapterConfig.REST(host, port, maxBodySizeBytes) =>
        new IngestRestGateway(host, port, maxBodySizeBytes)
      case ApiAdapterConfig.GRPC(host, port) =>
        throw new UnsupportedOperationException("gRPC gateway not yet implemented")
    }
}
