package com.cyrelis.srag.application.usecases.ingestion

import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.errors.PipelineError.ConfigurationError
import com.cyrelis.srag.application.model.ingestion.JobProcessingConfig
import com.cyrelis.srag.application.ports.{BlobStorePort, JobQueuePort}
import com.cyrelis.srag.domain.ingestionjob.{IngestionJob, IngestionJobRepository, JobStatus}
import com.cyrelis.srag.domain.transcript.IngestSource
import zio.{Clock, ZIO, ZLayer}

trait IngestService {
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

object IngestService {

  val layer: ZLayer[
    BlobStorePort & IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]] & JobQueuePort & JobProcessingConfig,
    Nothing,
    IngestService
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

  private final class IngestServiceLive(
    blobStore: BlobStorePort,
    jobRepository: IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]],
    jobConfig: JobProcessingConfig,
    jobQueue: JobQueuePort
  ) extends IngestService {

    override def submitAudio(
      audioContent: Array[Byte],
      mediaContentType: String,
      mediaFilename: String,
      metadata: Map[String, String]
    ): ZIO[Any, PipelineError, IngestionJob] =
      for {
        now     <- Clock.instant
        jobId    = UUID.randomUUID()
        blobKey <- blobStore.storeAudio(jobId, audioContent, mediaContentType, mediaFilename)
        job      = IngestionJob(
                id = jobId,
                transcriptId = None,
                source = IngestSource.Audio,
                mediaContentType = Some(mediaContentType),
                mediaFilename = Some(mediaFilename),
                status = JobStatus.Pending,
                attempt = 0,
                maxAttempts = jobConfig.maxAttempts,
                errorMessage = None,
                blobKey = Some(blobKey),
                metadata = metadata,
                createdAt = now,
                updatedAt = now
              )
        persisted <- jobRepository.create(job)
        _         <- jobQueue.enqueue(job.id)
      } yield persisted

    override def submitText(
      textContent: String,
      metadata: Map[String, String]
    ): ZIO[Any, PipelineError, IngestionJob] =
      for {
        now     <- Clock.instant
        jobId    = UUID.randomUUID()
        blobKey <- blobStore.storeText(jobId, textContent)
        job      = IngestionJob(
                id = jobId,
                transcriptId = None,
                source = IngestSource.Text,
                mediaContentType = None,
                mediaFilename = None,
                status = JobStatus.Pending,
                attempt = 0,
                maxAttempts = jobConfig.maxAttempts,
                errorMessage = None,
                blobKey = Some(blobKey),
                metadata = metadata,
                createdAt = now,
                updatedAt = now
              )
        persisted <- jobRepository.create(job)
        _         <- jobQueue.enqueue(job.id)
      } yield persisted

    override def submitDocument(
      documentContent: String,
      mediaType: String,
      metadata: Map[String, String]
    ): ZIO[Any, PipelineError, IngestionJob] =
      ZIO.fail(ConfigurationError("Document ingestion is not yet supported in async mode"))

    override def getJob(jobId: UUID): ZIO[Any, PipelineError, Option[IngestionJob]] =
      jobRepository.findById(jobId)
  }
}
