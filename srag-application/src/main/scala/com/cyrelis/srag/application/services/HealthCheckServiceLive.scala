package com.cyrelis.srag.application.services

import com.cyrelis.srag.application.ports.driven.datasource.DatasourcePort
import com.cyrelis.srag.application.ports.driven.embedding.EmbedderPort
import com.cyrelis.srag.application.ports.driven.job.JobQueuePort
import com.cyrelis.srag.application.ports.driven.reranker.RerankerPort
import com.cyrelis.srag.application.ports.driven.storage.{BlobStorePort, LexicalStorePort, VectorStorePort}
import com.cyrelis.srag.application.ports.driven.transcription.TranscriberPort
import com.cyrelis.srag.application.ports.driving.HealthCheckPort
import com.cyrelis.srag.application.types.HealthStatus
import zio.*

final class HealthCheckServiceLive(
  transcriber: TranscriberPort,
  embedder: EmbedderPort,
  datasource: DatasourcePort,
  vectorSink: VectorStorePort,
  lexicalStore: LexicalStorePort,
  reranker: RerankerPort,
  blobStore: BlobStorePort,
  jobQueue: JobQueuePort
) extends HealthCheckPort {

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
