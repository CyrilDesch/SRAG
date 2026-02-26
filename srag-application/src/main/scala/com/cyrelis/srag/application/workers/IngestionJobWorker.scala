package com.cyrelis.srag.application.workers

import java.time.Instant
import java.util.UUID

import scala.math.pow

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.pipeline.{
  AudioSourcePreparator,
  IndexingPipeline,
  SourcePreparator,
  TextSourcePreparator
}
import com.cyrelis.srag.application.ports.driven.job.JobQueuePort
import com.cyrelis.srag.application.ports.driven.storage.BlobStorePort
import com.cyrelis.srag.application.types.JobProcessingConfig
import com.cyrelis.srag.domain.ingestionjob.{IngestionJob, IngestionJobRepository, JobStatus}
import com.cyrelis.srag.domain.transcript.IngestSource
import zio.*

final class IngestionJobWorker(
  jobRepository: IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]],
  blobStore: BlobStorePort,
  audioPreparator: AudioSourcePreparator,
  textPreparator: TextSourcePreparator,
  indexingPipeline: IndexingPipeline,
  jobConfig: JobProcessingConfig,
  jobQueue: JobQueuePort
) {

  def run: ZIO[Any, Nothing, Unit] =
    Semaphore.make(jobConfig.maxConcurrentJobs.toLong).flatMap { semaphore =>
      (for {
        jobOpt <- jobQueue
                    .claim(blockingTimeoutSec = jobConfig.pollInterval.toSeconds.toInt)
                    .catchAll(err => ZIO.logWarning(s"claim failed: ${err.message}").as(None))
        _ <- jobOpt match {
               case Some(jobId) =>
                 semaphore
                   .withPermit(
                     processJob(jobId)
                   )
                   .forkDaemon
                   .unit
               case None =>
                 ZIO.unit
             }
      } yield ()).forever
    }

  private def processJob(jobId: UUID): ZIO[Any, Nothing, Unit] =
    ZIO.scoped {
      (for {
        _ <- ZIO.sleep((JobQueuePort.LockExpirationSeconds / 2).seconds)
        _ <- jobQueue
               .heartbeat(jobId)
               .catchAll(e => ZIO.logWarning(s"Heartbeat failed for $jobId: ${e.message}"))
      } yield ()).forever.forkScoped *>
        (ZIO.logDebug(s"Claimed job: $jobId") *>
          processById(jobId).foldZIO(
            err => handleFailureById(jobId, err),
            _ =>
              for {
                _ <- ZIO.logDebug(s"Job $jobId processed successfully, acknowledging")
                _ <-
                  jobQueue.ack(jobId).catchAll(e => ZIO.logWarning(s"ack failed for $jobId: ${e.message}"))
              } yield ()
          ))
    }

  private def processById(jobId: UUID): ZIO[Any, PipelineError, Unit] =
    for {
      _      <- ZIO.logDebug(s"Starting processing for job $jobId")
      jobOpt <- jobRepository.findById(jobId)
      _      <- ZIO.logDebug(s"Job $jobId lookup: ${if (jobOpt.isDefined) "found" else "not found"}")
      job    <- ZIO.fromOption(jobOpt).orElseFail(PipelineError.DatabaseError(s"Job $jobId not found", None))
      _      <- ZIO.logDebug(s"Job $jobId status: ${job.status}, attempt: ${job.attempt}/${job.maxAttempts}")
      _      <- processSingleJob(job)
      _      <- ZIO.logDebug(s"Completed processing for job $jobId")
    } yield ()

  private def processSingleJob(job: IngestionJob): ZIO[Any, PipelineError, Unit] = {
    val preparator = selectPreparator(job.source)

    for {
      _          <- ZIO.logDebug(s"Processing job ${job.id} (source: ${job.source}) - incrementing attempt (${job.attempt + 1})")
      claimedJob <- ZIO.succeed(job.incrementAttempt().markTranscribing())
      jobState1  <- jobRepository.update(claimedJob)
      _          <- ZIO.logDebug(s"Job ${jobState1.id} - preparing ${jobState1.source} content")
      transcript <- preparator.prepare(jobState1)
      jobWithTid <- jobRepository.update(jobState1.copy(transcriptId = Some(transcript.id)))
      _          <- ZIO.logDebug(s"Job ${jobWithTid.id} - marking as embedding")
      jobState2  <- jobRepository.update(jobWithTid.markEmbedding())
      _          <- indexingPipeline.index(transcript, jobState2)
      _          <- ZIO.logDebug(s"Job ${jobState2.id} - marking as success")
      finalState <- jobRepository.update(jobState2.markSuccess())
      _          <- cleanupBlob(finalState)
      _          <- ZIO.logDebug(s"Job ${finalState.id} - processing completed successfully")
    } yield ()
  }

  private def selectPreparator(source: IngestSource): SourcePreparator = source match {
    case IngestSource.Audio => audioPreparator
    case IngestSource.Text  => textPreparator
    case _                  => audioPreparator
  }

  private def cleanupBlob(job: IngestionJob): ZIO[Any, Nothing, Unit] =
    job.blobKey match {
      case Some(blobKey) =>
        ZIO.logDebug(s"Job ${job.id} - deleting blob $blobKey") *>
          blobStore
            .deleteBlob(blobKey)
            .catchAll(error => ZIO.logWarning(s"Failed to delete blob for job ${job.id}: ${error.message}"))
      case None =>
        ZIO.logDebug(s"Job ${job.id} - no blob to cleanup")
    }

  private def handleFailure(job: IngestionJob, error: PipelineError): ZIO[Any, Nothing, Unit] =
    for {
      _         <- ZIO.logDebug(s"Handling failure for job ${job.id}: ${error.message}")
      now       <- Clock.instant
      latestJob <-
        jobRepository
          .findById(job.id)
          .catchAll(err =>
            ZIO.logError(s"Failed to load job ${job.id} during failure handling: ${err.message}").as(Some(job))
          )
          .map(_.getOrElse(job))
      _ <-
        ZIO.logDebug(
          s"Job ${latestJob.id} current status: ${latestJob.status}, attempt: ${latestJob.attempt}/${latestJob.maxAttempts}"
        )
      _ <-
        if (latestJob.status == JobStatus.Success) {
          ZIO.logDebug(s"Job ${latestJob.id} already succeeded, skipping failure handling")
        } else {
          val retryAt    = computeNextRetry(latestJob, now)
          val updatedJob =
            if (retryAt.isDefined) latestJob.markFailed(error.message) else latestJob.markDeadLetter(error.message)
          for {
            _ <-
              ZIO.logDebug(
                s"Job ${latestJob.id} - computing retry: retryAt=${retryAt.map(_.toString).getOrElse("none")}, will mark as ${updatedJob.status}"
              )
            updateOk <- jobRepository
                          .update(updatedJob)
                          .as(true)
                          .catchAll(err =>
                            ZIO.logError(s"Failed to update job ${updatedJob.id} after error: ${err.message}").as(false)
                          )
            _ <- ZIO.logDebug(s"Job ${updatedJob.id} state update: ${if (updateOk) "success" else "failed"}")
            _ <-
              ZIO.logError(
                s"Ingestion job ${job.id} failed with '${error.message}'. Next retry at ${retryAt.map(_.toString).getOrElse("n/a")} (attempt ${updatedJob.attempt}/${updatedJob.maxAttempts})"
              )
            _ <- if (updateOk) {
                   retryAt match
                     case Some(next) =>
                       val delayMs = java.time.Duration.between(now, next).toMillis
                       (for {
                         _ <- ZIO.logDebug(s"Job ${job.id} - scheduling retry in ${delayMs}ms (at $next)")
                         _ <- jobQueue
                                .release(job.id)
                                .catchAll(e => ZIO.logWarning(s"release failed for ${job.id}: ${e.message}"))
                         _ <- ZIO.sleep(zio.Duration.fromMillis(delayMs))
                         _ <- jobQueue
                                .enqueue(job.id)
                                .catchAll(e => ZIO.logWarning(s"retry enqueue failed for ${job.id}: ${e.message}"))
                       } yield ()).forkDaemon.unit
                     case None =>
                       ZIO.logDebug(s"Job ${job.id} - max attempts reached, sending to dead letter queue") *>
                         jobQueue
                           .ack(job.id)
                           .catchAll(e => ZIO.logWarning(s"ack failed for ${job.id}: ${e.message}")) *>
                         jobQueue
                           .deadLetter(job.id, error.message)
                           .catchAll(e => ZIO.logWarning(s"dead-letter failed for ${job.id}: ${e.message}"))
                 } else {
                   ZIO.logWarning(
                     s"Skipping re-enqueue for ${job.id} because state persistence failed; manual intervention may be required"
                   )
                 }
          } yield ()
        }
    } yield ()

  private def handleFailureById(jobId: java.util.UUID, error: PipelineError): ZIO[Any, Nothing, Unit] =
    for {
      _      <- ZIO.logDebug(s"Handling failure for job $jobId (by ID): ${error.message}")
      now    <- Clock.instant
      jobOpt <- jobRepository.findById(jobId).orElseSucceed(None)
      _      <- ZIO.logDebug(s"Job $jobId lookup during failure handling: ${
               if (jobOpt.isDefined) "found" else "not found, creating placeholder"
             }")
      baseJob = jobOpt.getOrElse(
                  IngestionJob(
                    id = jobId,
                    transcriptId = None,
                    source = IngestSource.Audio,
                    mediaContentType = None,
                    mediaFilename = None,
                    status = JobStatus.Failed,
                    attempt = 0,
                    maxAttempts = 1,
                    errorMessage = Some(error.message),
                    blobKey = None,
                    metadata = Map.empty,
                    createdAt = now,
                    updatedAt = now
                  )
                )
      _ <- handleFailure(baseJob, error)
    } yield ()

  private def computeNextRetry(job: IngestionJob, now: Instant): Option[Instant] =
    if (job.attempt >= job.maxAttempts) None
    else {
      val rawDelayMs = jobConfig.initialRetryDelay.toMillis * pow(jobConfig.backoffFactor, job.attempt.toDouble - 1.0)
      val cappedMs   = math.min(rawDelayMs, jobConfig.maxRetryDelay.toMillis.toDouble).toLong
      Some(now.plusMillis(cappedMs))
    }
}
