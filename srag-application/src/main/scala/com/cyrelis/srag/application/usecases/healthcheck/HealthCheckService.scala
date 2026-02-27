package com.cyrelis.srag.application.usecases.healthcheck

import com.cyrelis.srag.application.model.healthcheck.HealthStatus
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
import zio.*

trait HealthCheckService {
  def checkAllServices(): Task[List[HealthStatus]]
}

object HealthCheckService {

  val layer: ZLayer[
    TranscriberPort & EmbedderPort & DatasourcePort & VectorStorePort & LexicalStorePort & RerankerPort &
      BlobStorePort & JobQueuePort,
    Nothing,
    HealthCheckService
  ] =
    ZLayer {
      for {
        transcriber <- ZIO.service[TranscriberPort]
        embedder    <- ZIO.service[EmbedderPort]
        datasource  <- ZIO.service[DatasourcePort]
        vectorSink  <- ZIO.service[VectorStorePort]
        lexical     <- ZIO.service[LexicalStorePort]
        reranker    <- ZIO.service[RerankerPort]
        blobStore   <- ZIO.service[BlobStorePort]
        jobQueue    <- ZIO.service[JobQueuePort]
      } yield new HealthCheckServiceLive(
        transcriber = transcriber,
        embedder = embedder,
        datasource = datasource,
        vectorSink = vectorSink,
        lexicalStore = lexical,
        reranker = reranker,
        blobStore = blobStore,
        jobQueue = jobQueue
      )
    }

  private final class HealthCheckServiceLive(
    transcriber: TranscriberPort,
    embedder: EmbedderPort,
    datasource: DatasourcePort,
    vectorSink: VectorStorePort,
    lexicalStore: LexicalStorePort,
    reranker: RerankerPort,
    blobStore: BlobStorePort,
    jobQueue: JobQueuePort
  ) extends HealthCheckService {

    override def checkAllServices(): Task[List[HealthStatus]] =
      ZIO.collectAllPar(
        List(
          transcriber.healthCheck(),
          embedder.healthCheck(),
          datasource.healthCheck(),
          vectorSink.healthCheck(),
          lexicalStore.healthCheck(),
          reranker.healthCheck(),
          blobStore.healthCheck(),
          jobQueue.healthCheck()
        )
      )
  }
}
