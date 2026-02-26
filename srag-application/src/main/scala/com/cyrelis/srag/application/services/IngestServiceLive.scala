package com.cyrelis.srag.application.services

import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.errors.PipelineError.ConfigurationError
import com.cyrelis.srag.application.ports.driven.job.JobQueuePort
import com.cyrelis.srag.application.ports.driven.storage.BlobStorePort
import com.cyrelis.srag.application.ports.driving.IngestPort
import com.cyrelis.srag.application.types.JobProcessingConfig
import com.cyrelis.srag.domain.ingestionjob.{IngestionJob, IngestionJobRepository, JobStatus}
import com.cyrelis.srag.domain.transcript.IngestSource
import zio.{Clock, ZIO}

final class IngestServiceLive(
  blobStore: BlobStorePort,
  jobRepository: IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]],
  jobConfig: JobProcessingConfig,
  jobQueue: JobQueuePort
) extends IngestPort {

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
