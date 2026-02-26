package com.cyrelis.srag.infrastructure.runtime

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.pipeline.{AudioSourcePreparator, IndexingPipeline, TextSourcePreparator}
import com.cyrelis.srag.application.ports.driven.datasource.DatasourcePort
import com.cyrelis.srag.application.ports.driven.embedding.EmbedderPort
import com.cyrelis.srag.application.ports.driven.job.JobQueuePort
import com.cyrelis.srag.application.ports.driven.reranker.RerankerPort
import com.cyrelis.srag.application.ports.driven.storage.{BlobStorePort, LexicalStorePort, VectorStorePort}
import com.cyrelis.srag.application.ports.driven.transcription.TranscriberPort
import com.cyrelis.srag.application.ports.driving.{HealthCheckPort, IngestPort, QueryPort}
import com.cyrelis.srag.application.types.JobProcessingConfig
import com.cyrelis.srag.application.workers.IngestionJobWorker
import com.cyrelis.srag.domain.ingestionjob.IngestionJobRepository
import com.cyrelis.srag.domain.transcript.TranscriptRepository
import com.cyrelis.srag.infrastructure.config.RuntimeConfig
import zio.*

trait ServiceWiring {

  val audioSourcePreparatorLayer: ZLayer[BlobStorePort & TranscriberPort, Nothing, AudioSourcePreparator] =
    ZLayer {
      for {
        blobStore   <- ZIO.service[BlobStorePort]
        transcriber <- ZIO.service[TranscriberPort]
      } yield new AudioSourcePreparator(blobStore, transcriber)
    }

  val textSourcePreparatorLayer: ZLayer[BlobStorePort, Nothing, TextSourcePreparator] =
    ZLayer {
      for {
        blobStore <- ZIO.service[BlobStorePort]
      } yield new TextSourcePreparator(blobStore)
    }

  val commonIndexingPipelineLayer: ZLayer[
    TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]] & EmbedderPort & VectorStorePort & LexicalStorePort,
    Nothing,
    IndexingPipeline
  ] =
    ZLayer {
      for {
        transcriptRepository <- ZIO.service[TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]]]
        embedder             <- ZIO.service[EmbedderPort]
        vectorSink           <- ZIO.service[VectorStorePort]
        lexicalStore         <- ZIO.service[LexicalStorePort]
      } yield new IndexingPipeline(transcriptRepository, embedder, vectorSink, lexicalStore)
    }

  val jobProcessingConfigLayer: ZLayer[RuntimeConfig, Nothing, JobProcessingConfig] =
    ZLayer {
      ZIO.service[RuntimeConfig].map(_.jobProcessing)
    }

  val jobWorkerLayer: ZLayer[
    IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]] & BlobStorePort & AudioSourcePreparator &
      TextSourcePreparator & IndexingPipeline & JobQueuePort & RuntimeConfig,
    Nothing,
    IngestionJobWorker
  ] =
    ZLayer {
      for {
        jobRepository    <- ZIO.service[IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]]]
        blobStore        <- ZIO.service[BlobStorePort]
        audioPreparator  <- ZIO.service[AudioSourcePreparator]
        textPreparator   <- ZIO.service[TextSourcePreparator]
        indexingPipeline <- ZIO.service[IndexingPipeline]
        jobQueue         <- ZIO.service[JobQueuePort]
        config           <- ZIO.service[RuntimeConfig]
      } yield new IngestionJobWorker(
        jobRepository = jobRepository,
        blobStore = blobStore,
        audioPreparator = audioPreparator,
        textPreparator = textPreparator,
        indexingPipeline = indexingPipeline,
        jobConfig = config.jobProcessing,
        jobQueue = jobQueue
      )
    }

}

object ServiceModule extends ServiceWiring {
  type ServiceEnvironment = AudioSourcePreparator & TextSourcePreparator & IndexingPipeline & IngestPort &
    IngestionJobWorker & QueryPort & HealthCheckPort

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
    audioSourcePreparatorLayer,
    textSourcePreparatorLayer,
    commonIndexingPipelineLayer,
    jobProcessingConfigLayer,
    IngestPort.live,
    jobWorkerLayer,
    QueryPort.live,
    HealthCheckPort.live
  )
}
