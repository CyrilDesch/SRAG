package com.cyrelis.srag.infrastructure.config

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.ports.{
  BlobStorePort,
  DatasourcePort,
  EmbedderPort,
  JobQueuePort,
  LexicalStorePort,
  RerankerPort,
  TranscriberPort,
  VectorStorePort
}
import com.cyrelis.srag.domain.ingestionjob.IngestionJobRepository
import com.cyrelis.srag.domain.transcript.TranscriptRepository
import com.cyrelis.srag.infrastructure.adapters.driven.blobstore.MinioAdapter
import com.cyrelis.srag.infrastructure.adapters.driven.database.postgres.{
  PostgresDatasource,
  PostgresJobRepository,
  PostgresTranscriptRepository
}
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
        ZLayer.succeed(cfg) >>> PostgresDatasource.layer
    }

  def createTranscriptRepositoryLayer(
    config: DatabaseAdapterConfig
  ): ZLayer[DatasourcePort, Throwable, TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]]] =
    config match {
      case _: DatabaseAdapterConfig.Postgres =>
        PostgresTranscriptRepository.layer
    }

  def createJobRepositoryLayer(
    config: DatabaseAdapterConfig
  ): ZLayer[DatasourcePort, Throwable, IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]]] =
    config match {
      case _: DatabaseAdapterConfig.Postgres =>
        PostgresJobRepository.layer
    }

  def createVectorStoreLayer(config: VectorStoreAdapterConfig): ZLayer[Any, Throwable, VectorStorePort] =
    config match {
      case cfg: VectorStoreAdapterConfig.Qdrant =>
        ZLayer.succeed(cfg) >>> QdrantAdapter.layer
    }

  def createLexicalStoreLayer(config: LexicalStoreAdapterConfig): ZLayer[Any, Throwable, LexicalStorePort] =
    config match {
      case cfg: LexicalStoreAdapterConfig.OpenSearch =>
        ZLayer.succeed(cfg) >>> OpenSearchAdapter.layer
    }

  def createRerankerLayer(config: RerankerAdapterConfig): ZLayer[Any, Throwable, RerankerPort] =
    config match {
      case cfg: RerankerAdapterConfig.Transformers =>
        ZLayer.succeed(cfg) >>> TransformersRerankerAdapter.layer
    }

  def createTranscriberLayer(config: TranscriberAdapterConfig): ZLayer[Any, Throwable, TranscriberPort] =
    config match {
      case cfg: TranscriberAdapterConfig.Whisper =>
        ZLayer.succeed(cfg) >>> WhisperAdapter.layer
      case cfg: TranscriberAdapterConfig.AssemblyAI =>
        ZLayer.succeed(cfg) >>> AssemblyAIAdapter.layer
    }

  def createEmbedderLayer(config: EmbedderAdapterConfig): ZLayer[Any, Throwable, EmbedderPort] =
    config match {
      case cfg: EmbedderAdapterConfig.HuggingFace =>
        ZLayer.succeed(cfg) >>> HuggingFaceAdapter.layer
    }

  def createBlobStoreLayer(config: BlobStoreAdapterConfig): ZLayer[Any, Throwable, BlobStorePort] =
    config match {
      case cfg: BlobStoreAdapterConfig.MinIO =>
        ZLayer.succeed(cfg) >>> MinioAdapter.layer
    }

  def createJobQueueLayer(config: JobQueueAdapterConfig): ZLayer[Any, Throwable, JobQueuePort] =
    config match {
      case cfg: JobQueueAdapterConfig.Redis =>
        ZLayer.succeed(cfg) >>> RedisJobQueueAdapter.layer
    }

  def createGateway(config: ApiAdapterConfig): Gateway =
    config match {
      case ApiAdapterConfig.REST(host, port, maxBodySizeBytes) =>
        new IngestRestGateway(host, port, maxBodySizeBytes)
    }
}
