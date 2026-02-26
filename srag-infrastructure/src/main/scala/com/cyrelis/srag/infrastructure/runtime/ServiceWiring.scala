package com.cyrelis.srag.infrastructure.runtime

import com.cyrelis.srag.application.model.ingestion.JobProcessingConfig
import com.cyrelis.srag.application.ports.{
  BlobStorePort,
  EmbedderPort,
  JobQueuePort,
  LexicalStorePort,
  RerankerPort,
  TranscriberPort,
  VectorStorePort
}
import com.cyrelis.srag.application.usecases.healthcheck.HealthCheckService
import com.cyrelis.srag.application.usecases.ingestion.pipeline.IndexingPipeline
import com.cyrelis.srag.application.usecases.ingestion.pipeline.preparator.{
  AudioPreparatorPipeline,
  PreparatorRouterPipeline,
  TextPreparatorPipeline
}
import com.cyrelis.srag.application.usecases.ingestion.{IngestService, IngestionWorker}
import com.cyrelis.srag.application.usecases.query.QueryService
import com.cyrelis.srag.domain.ingestionjob.IngestionJobRepository
import com.cyrelis.srag.domain.transcript.TranscriptRepository
import com.cyrelis.srag.infrastructure.config.RuntimeConfig
import zio.*

trait ServiceWiring {

  val jobProcessingConfigLayer: ZLayer[RuntimeConfig, Nothing, JobProcessingConfig] =
    ZLayer {
      ZIO.service[RuntimeConfig].map(_.jobProcessing)
    }

}

object ServiceModule extends ServiceWiring {
  type ServiceEnvironment = AudioPreparatorPipeline & TextPreparatorPipeline & PreparatorRouterPipeline &
    IndexingPipeline & IngestService & IngestionWorker & QueryService & HealthCheckService

  type RequiredDrivenEnv = TranscriberPort & EmbedderPort & VectorStorePort & LexicalStorePort & RerankerPort &
    BlobStorePort & JobQueuePort

  val live: ZLayer[
    RuntimeConfig & DatabaseModule.DatabaseEnvironment & RequiredDrivenEnv,
    Nothing,
    ServiceEnvironment
  ] = ZLayer.makeSome[
    RuntimeConfig & DatabaseModule.DatabaseEnvironment & RequiredDrivenEnv,
    ServiceEnvironment
  ](
    AudioPreparatorPipeline.layer,
    TextPreparatorPipeline.layer,
    PreparatorRouterPipeline.layer,
    IndexingPipeline.layer,
    jobProcessingConfigLayer,
    IngestService.layer,
    IngestionWorker.layer,
    QueryService.layer,
    HealthCheckService.layer
  )
}
