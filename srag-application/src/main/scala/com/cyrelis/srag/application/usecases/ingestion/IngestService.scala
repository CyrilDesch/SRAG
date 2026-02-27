package com.cyrelis.srag.application.usecases.ingestion

import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.model.ingestion.JobProcessingConfig
import com.cyrelis.srag.application.ports.{BlobStorePort, JobQueuePort}
import com.cyrelis.srag.domain.ingestionjob.{IngestionJob, IngestionJobRepository, JobStatus}
import com.cyrelis.srag.domain.transcript.IngestSource
import zio.*

trait IngestService {
  def submitAudio(
    audioContent: Array[Byte],
    mediaContentType: String,
    mediaFilename: String,
    metadata: Map[String, String]
  ): ZIO[Any, PipelineError, IngestionJob]
  def submitText(textContent: String, metadata: Map[String, String]): ZIO[Any, PipelineError, IngestionJob]
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

    private val blobStoreTimeout = 15.seconds
    private val databaseTimeout  = 5.seconds
    private val queueTimeout     = 5.seconds

    private val blobStoreRetrySchedule =
      Schedule.exponential(100.millis, 2.0).modifyDelay((_, d) => if (d > 5.seconds) 5.seconds else d) &&
        Schedule.recurs(2)

    private val databaseRetrySchedule =
      Schedule.exponential(100.millis, 2.0).modifyDelay((_, d) => if (d > 5.seconds) 5.seconds else d) &&
        Schedule.recurs(2)

    private val queueRetrySchedule =
      Schedule.exponential(100.millis, 2.0).modifyDelay((_, d) => if (d > 5.seconds) 5.seconds else d) &&
        Schedule.recurs(2)

    override def submitAudio(
      audioContent: Array[Byte],
      mediaContentType: String,
      mediaFilename: String,
      metadata: Map[String, String]
    ): ZIO[Any, PipelineError, IngestionJob] =
      for {
        now     <- Clock.instant
        jobId    = UUID.randomUUID()
        blobKey <- blobStore
                     .storeAudio(jobId, audioContent, mediaContentType, mediaFilename)
                     .timeoutFail(
                       PipelineError.TimeoutError(
                         operation = s"ingest.submit_audio.store_blob.$jobId",
                         timeoutMs = blobStoreTimeout.toMillis
                       )
                     )(blobStoreTimeout)
                     .retry(blobStoreRetrySchedule)
        job = IngestionJob(
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
        persisted <- jobRepository
                       .create(job)
                       .timeoutFail(
                         PipelineError.TimeoutError(
                           operation = s"ingest.submit_audio.persist_job.$jobId",
                           timeoutMs = databaseTimeout.toMillis
                         )
                       )(databaseTimeout)
                       .retry(databaseRetrySchedule)
        _ <- jobQueue
               .enqueue(job.id)
               .timeoutFail(
                 PipelineError.TimeoutError(
                   operation = s"ingest.submit_audio.enqueue_job.${job.id}",
                   timeoutMs = queueTimeout.toMillis
                 )
               )(queueTimeout)
               .retry(queueRetrySchedule)
      } yield persisted

    override def submitText(
      textContent: String,
      metadata: Map[String, String]
    ): ZIO[Any, PipelineError, IngestionJob] =
      for {
        now     <- Clock.instant
        jobId    = UUID.randomUUID()
        blobKey <- blobStore
                     .storeText(jobId, textContent)
                     .timeoutFail(
                       PipelineError.TimeoutError(
                         operation = s"ingest.submit_text.store_blob.$jobId",
                         timeoutMs = blobStoreTimeout.toMillis
                       )
                     )(blobStoreTimeout)
                     .retry(blobStoreRetrySchedule)
        job = IngestionJob(
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
        persisted <- jobRepository
                       .create(job)
                       .timeoutFail(
                         PipelineError.TimeoutError(
                           operation = s"ingest.submit_text.persist_job.$jobId",
                           timeoutMs = databaseTimeout.toMillis
                         )
                       )(databaseTimeout)
                       .retry(databaseRetrySchedule)
        _ <- jobQueue
               .enqueue(job.id)
               .timeoutFail(
                 PipelineError.TimeoutError(
                   operation = s"ingest.submit_text.enqueue_job.${job.id}",
                   timeoutMs = queueTimeout.toMillis
                 )
               )(queueTimeout)
               .retry(queueRetrySchedule)
      } yield persisted

    override def getJob(jobId: UUID): ZIO[Any, PipelineError, Option[IngestionJob]] =
      jobRepository
        .findById(jobId)
        .timeoutFail(
          PipelineError.TimeoutError(
            operation = s"ingest.get_job.$jobId",
            timeoutMs = databaseTimeout.toMillis
          )
        )(databaseTimeout)
        .retry(databaseRetrySchedule)
  }
}
