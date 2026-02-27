package com.cyrelis.srag.application.usecases.ingestion

import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.testsupport.{ApplicationSharedLayers, ApplicationSharedSpec}
import com.cyrelis.srag.domain.ingestionjob.JobStatus
import com.cyrelis.srag.domain.transcript.IngestSource
import zio.*
import zio.test.*

object IngestServiceSpec extends ApplicationSharedSpec {

  override def spec =
    suite("IngestService")(
      test("submitText stores text, creates pending job and enqueues matching job id") {
        for {
          refs <- makeRefs
          job  <- (for {
                   service <- ZIO.service[IngestService]
                   job     <- service.submitText("hello world", Map("tenant" -> "acme"))
                 } yield job).provideLayer(
                   ingestLayer(
                     makeBlobStore(refs),
                     makeRepository(refs),
                     makeQueue(refs)
                   )
                 )
          storedText <- refs.storedText.get
          created    <- refs.createdJobs.get
          enqueued   <- refs.enqueued.get
        } yield {
          val createdJob = created.head
          assertTrue(
            storedText.size == 1,
            storedText.head == (job.id, "hello world"),
            created.size == 1,
            enqueued == Vector(job.id),
            createdJob.id == job.id,
            createdJob.status == JobStatus.Pending,
            createdJob.source == IngestSource.Text,
            createdJob.blobKey.contains(s"text-${job.id}"),
            createdJob.mediaContentType.isEmpty,
            createdJob.mediaFilename.isEmpty,
            createdJob.attempt == 0,
            createdJob.maxAttempts == ApplicationSharedLayers.sharedJobProcessingConfig.maxAttempts,
            createdJob.metadata == Map("tenant" -> "acme"),
            createdJob.createdAt == createdJob.updatedAt
          )
        }
      },
      test("submitAudio stores audio, creates pending job and enqueues matching job id") {
        for {
          refs <- makeRefs
          job  <- (for {
                   service <- ZIO.service[IngestService]
                   job     <- service.submitAudio(
                            audioContent = Array[Byte](1, 2, 3),
                            mediaContentType = "audio/mpeg",
                            mediaFilename = "sample.mp3",
                            metadata = Map("tenant" -> "acme")
                          )
                 } yield job).provideLayer(
                   ingestLayer(
                     makeBlobStore(refs),
                     makeRepository(refs),
                     makeQueue(refs)
                   )
                 )
          storedAudio <- refs.storedAudio.get
          created     <- refs.createdJobs.get
          enqueued    <- refs.enqueued.get
        } yield {
          val createdJob = created.head
          assertTrue(
            storedAudio.size == 1,
            storedAudio.head == (job.id, "audio/mpeg", "sample.mp3"),
            created.size == 1,
            enqueued == Vector(job.id),
            createdJob.id == job.id,
            createdJob.status == JobStatus.Pending,
            createdJob.source == IngestSource.Audio,
            createdJob.blobKey.contains(s"audio-${job.id}"),
            createdJob.mediaContentType.contains("audio/mpeg"),
            createdJob.mediaFilename.contains("sample.mp3"),
            createdJob.attempt == 0,
            createdJob.maxAttempts == ApplicationSharedLayers.sharedJobProcessingConfig.maxAttempts,
            createdJob.metadata == Map("tenant" -> "acme"),
            createdJob.createdAt == createdJob.updatedAt
          )
        }
      },
      test("getJob returns existing job when present") {
        val existingJob = sampleJob(
          id = UUID.randomUUID(),
          source = IngestSource.Audio,
          status = JobStatus.Pending,
          attempt = 0,
          maxAttempts = 3,
          blobKey = Some("audio-existing"),
          mediaContentType = Some("audio/mpeg"),
          mediaFilename = Some("existing.mp3"),
          metadata = Map("tenant" -> "acme")
        )

        for {
          refs <- makeRefs
          _    <- refs.createdJobs.set(Vector(existingJob))
          job  <- (for {
                   service <- ZIO.service[IngestService]
                   value   <- service.getJob(existingJob.id)
                 } yield value).provideLayer(
                   ingestLayer(
                     makeBlobStore(refs),
                     makeRepository(refs),
                     makeQueue(refs)
                   )
                 )
        } yield assertTrue(job.contains(existingJob))
      },
      test("getJob returns none when job does not exist") {
        val unknownId = UUID.randomUUID()
        for {
          refs <- makeRefs
          job  <- (for {
                   service <- ZIO.service[IngestService]
                   value   <- service.getJob(unknownId)
                 } yield value).provideLayer(
                   ingestLayer(
                     makeBlobStore(refs),
                     makeRepository(refs),
                     makeQueue(refs)
                   )
                 )
        } yield assertTrue(job.isEmpty)
      },
      test("submitAudio propagates queue failure after blob storage and persistence") {
        val queueFailure = PipelineError.QueueError("queue temporarily unavailable")
        for {
          refs   <- makeRefs
          result <- (for {
                      service <- ZIO.service[IngestService]
                      value   <- service
                                 .submitAudio(
                                   audioContent = Array[Byte](1, 2, 3),
                                   mediaContentType = "audio/mpeg",
                                   mediaFilename = "sample.mp3",
                                   metadata = Map("source" -> "integration-test")
                                 )
                                 .either
                    } yield value).provideLayer(
                      ingestLayer(
                        makeBlobStore(refs),
                        makeRepository(refs),
                        makeQueue(refs, _ => ZIO.fail(queueFailure))
                      )
                    )
          storedAudio <- refs.storedAudio.get
          created     <- refs.createdJobs.get
          enqueued    <- refs.enqueued.get
        } yield {
          val createdJob = created.head
          assertTrue(
            result == Left(queueFailure),
            storedAudio.size == 1,
            storedAudio.head == (createdJob.id, "audio/mpeg", "sample.mp3"),
            created.size == 1,
            enqueued.nonEmpty,
            enqueued.forall(_ == createdJob.id),
            createdJob.source == IngestSource.Audio,
            createdJob.status == JobStatus.Pending,
            createdJob.blobKey.contains(s"audio-${createdJob.id}")
          )
        }
      }
    ) @@ TestAspect.withLiveClock
}
