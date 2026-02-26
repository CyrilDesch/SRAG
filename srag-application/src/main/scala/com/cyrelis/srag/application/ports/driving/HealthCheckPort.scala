package com.cyrelis.srag.application.ports.driving

import com.cyrelis.srag.application.ports.driven.datasource.DatasourcePort
import com.cyrelis.srag.application.ports.driven.embedding.EmbedderPort
import com.cyrelis.srag.application.ports.driven.job.JobQueuePort
import com.cyrelis.srag.application.ports.driven.reranker.RerankerPort
import com.cyrelis.srag.application.ports.driven.storage.{BlobStorePort, LexicalStorePort, VectorStorePort}
import com.cyrelis.srag.application.ports.driven.transcription.TranscriberPort
import com.cyrelis.srag.application.services.HealthCheckServiceLive
import com.cyrelis.srag.application.types.HealthStatus
import zio.*

trait HealthCheckPort {
  def checkAllServices(): Task[List[HealthStatus]]
}

object HealthCheckPort {

  val live: ZLayer[
    TranscriberPort & EmbedderPort & DatasourcePort & VectorStorePort & LexicalStorePort & RerankerPort &
      BlobStorePort & JobQueuePort,
    Nothing,
    HealthCheckPort
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
}
