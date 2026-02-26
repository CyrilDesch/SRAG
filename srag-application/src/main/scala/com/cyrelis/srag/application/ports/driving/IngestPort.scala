package com.cyrelis.srag.application.ports.driving

import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.ports.driven.job.JobQueuePort
import com.cyrelis.srag.application.ports.driven.storage.BlobStorePort
import com.cyrelis.srag.application.services.IngestServiceLive
import com.cyrelis.srag.application.types.JobProcessingConfig
import com.cyrelis.srag.domain.ingestionjob.{IngestionJob, IngestionJobRepository}
import zio.*

trait IngestPort {
  def submitAudio(
    audioContent: Array[Byte],
    mediaContentType: String,
    mediaFilename: String,
    metadata: Map[String, String]
  ): ZIO[Any, PipelineError, IngestionJob]
  def submitText(textContent: String, metadata: Map[String, String]): ZIO[Any, PipelineError, IngestionJob]
  def submitDocument(
    documentContent: String,
    mediaType: String,
    metadata: Map[String, String]
  ): ZIO[Any, PipelineError, IngestionJob]
  def getJob(jobId: UUID): ZIO[Any, PipelineError, Option[IngestionJob]]
}

object IngestPort {

  val live: ZLayer[
    BlobStorePort & IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]] & JobQueuePort & JobProcessingConfig,
    Nothing,
    IngestPort
  ] =
    ZLayer {
      for {
        blobStore     <- ZIO.service[BlobStorePort]
        jobRepository <- ZIO.service[IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]]]
        jobQueue      <- ZIO.service[JobQueuePort]
        jobConfig     <- ZIO.service[JobProcessingConfig]
      } yield new IngestServiceLive(
        blobStore = blobStore,
        jobRepository = jobRepository,
        jobConfig = jobConfig,
        jobQueue = jobQueue
      )
    }
}
