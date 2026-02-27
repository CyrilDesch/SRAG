package com.cyrelis.srag.application.usecases.ingestion

import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.testsupport.ApplicationSharedSpec
import com.cyrelis.srag.domain.ingestionjob.JobStatus
import com.cyrelis.srag.domain.transcript.IngestSource
import zio.*
import zio.test.*

object IngestionWorkerSpec extends ApplicationSharedSpec {

  override def spec =
    suite("IngestionWorker")(
      test("run processes one claimed job to success, cleans blob and acknowledges queue") {
        val jobId      = UUID.randomUUID()
        val initialJob = sampleJob(
          id = jobId,
          source = IngestSource.Text,
          status = JobStatus.Pending,
          attempt = 0,
          maxAttempts = 3,
          blobKey = Some("blob-success"),
          metadata = Map("tenant" -> "acme")
        )
        val preparedResult = sampleTranscript(
          id = UUID.randomUUID(),
          source = IngestSource.Text,
          text = "hello worker",
          metadata = Map("tenant" -> "acme")
        )

        for {
          refs      <- makeWorkerRefs(Map(jobId -> initialJob))
          ackSignal <- Promise.make[Nothing, Unit]
          queue     <- makeWorkerQueue(
                     refs = refs,
                     claimedJobId = jobId,
                     onAck = _ => ackSignal.succeed(()).unit
                   )
          fiber <- ZIO
                     .serviceWithZIO[IngestionWorker](_.run)
                     .provideLayer(
                       workerLayer(
                         makeWorkerRepository(refs),
                         makeWorkerBlobStore(refs),
                         makeWorkerPreparatorRouter(refs, _ => ZIO.succeed(preparedResult)),
                         makeWorkerIndexingPipeline(refs),
                         queue
                       )
                     )
                     .fork
          ackedInTime <- ackSignal.await.timeout(2.seconds)
          _           <- fiber.interrupt
          jobs        <- refs.jobs.get
          prepared    <- refs.preparedJobs.get
          indexed     <- refs.indexedJobs.get
          deleted     <- refs.deletedBlobs.get
          acked       <- refs.acked.get
          dead        <- refs.deadLettered.get
        } yield {
          val finalJob = jobs(jobId)
          assertTrue(
            ackedInTime.isDefined,
            finalJob.status == JobStatus.Success,
            finalJob.transcriptId.contains(preparedResult.id),
            prepared == Vector(jobId),
            indexed == Vector(jobId),
            deleted == Vector("blob-success"),
            acked == Vector(jobId),
            dead.isEmpty
          )
        }
      },
      test("run dead-letters job when preparation fails at max attempts") {
        val jobId      = UUID.randomUUID()
        val initialJob = sampleJob(
          id = jobId,
          source = IngestSource.Text,
          status = JobStatus.Pending,
          attempt = 0,
          maxAttempts = 1,
          blobKey = Some("blob-failure")
        )
        val prepFailure = PipelineError.TranscriptionError("cannot transcribe")

        for {
          refs       <- makeWorkerRefs(Map(jobId -> initialJob))
          deadSignal <- Promise.make[Nothing, Unit]
          queue      <- makeWorkerQueue(
                     refs = refs,
                     claimedJobId = jobId,
                     onDeadLetter = (_, _) => deadSignal.succeed(()).unit
                   )
          fiber <- ZIO
                     .serviceWithZIO[IngestionWorker](_.run)
                     .provideLayer(
                       workerLayer(
                         makeWorkerRepository(refs),
                         makeWorkerBlobStore(refs),
                         makeWorkerPreparatorRouter(refs, _ => ZIO.fail(prepFailure)),
                         makeWorkerIndexingPipeline(refs),
                         queue
                       )
                     )
                     .fork
          deadInTime   <- deadSignal.await.timeout(2.seconds)
          _            <- fiber.interrupt
          jobs         <- refs.jobs.get
          indexed      <- refs.indexedJobs.get
          deleted      <- refs.deletedBlobs.get
          acked        <- refs.acked.get
          deadLettered <- refs.deadLettered.get
        } yield {
          val finalJob = jobs(jobId)
          assertTrue(
            deadInTime.isDefined,
            finalJob.status == JobStatus.DeadLetter,
            finalJob.errorMessage.contains("cannot transcribe"),
            indexed.isEmpty,
            deleted.isEmpty,
            acked == Vector(jobId),
            deadLettered == Vector((jobId, "cannot transcribe"))
          )
        }
      }
    ) @@ TestAspect.withLiveClock
}
