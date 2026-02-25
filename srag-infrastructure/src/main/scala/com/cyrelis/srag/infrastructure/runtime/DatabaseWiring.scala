package com.cyrelis.srag.infrastructure.runtime

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.ports.driven.datasource.DatasourcePort
import com.cyrelis.srag.domain.ingestionjob.IngestionJobRepository
import com.cyrelis.srag.domain.transcript.TranscriptRepository
import com.cyrelis.srag.infrastructure.config.{AdapterFactory, RuntimeConfig}
import com.cyrelis.srag.infrastructure.resilience.RetryWrappers
import zio.*

trait DatabaseWiring {
  val datasourceLayer: ZLayer[RuntimeConfig, Throwable, DatasourcePort] =
    ZLayer {
      for {
        config     <- ZIO.service[RuntimeConfig]
        datasource <- ZIO
                        .service[DatasourcePort]
                        .provide(
                          AdapterFactory.createDatasourceLayer(config.adapters.driven.database)
                        )
      } yield datasource
    }

  val transcriptRepositoryLayer
    : ZLayer[RuntimeConfig & DatasourcePort, Throwable, TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]]] =
    ZLayer {
      for {
        config   <- ZIO.service[RuntimeConfig]
        _        <- ZIO.service[DatasourcePort]
        baseRepo <- ZIO
                      .service[TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]]]
                      .provideSome[DatasourcePort](
                        AdapterFactory.createTranscriptRepositoryLayer(config.adapters.driven.database)
                      )
        repo = RetryWrappers.wrapTranscriptRepository(baseRepo, config.retry, config.timeouts)
      } yield repo
    }

  val jobRepositoryLayer
    : ZLayer[RuntimeConfig & DatasourcePort, Throwable, IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]]] =
    ZLayer {
      for {
        config  <- ZIO.service[RuntimeConfig]
        _       <- ZIO.service[DatasourcePort]
        jobRepo <- ZIO
                     .service[IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]]]
                     .provideSome[DatasourcePort](
                       AdapterFactory.createJobRepositoryLayer(config.adapters.driven.database)
                     )
        wrappedRepo = RetryWrappers.wrapJobRepository(jobRepo, config.retry, config.timeouts)
      } yield wrappedRepo
    }
}

object DatabaseModule extends DatabaseWiring {
  type DatabaseEnvironment =
    DatasourcePort & TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]] &
      IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]]

  val live: ZLayer[RuntimeConfig, Throwable, DatabaseEnvironment] =
    ZLayer.makeSome[RuntimeConfig, DatabaseEnvironment](
      datasourceLayer,
      transcriptRepositoryLayer,
      jobRepositoryLayer
    )
}
