package com.cyrelis.srag.application.usecases.ingestion.pipeline.preparator

import java.nio.charset.StandardCharsets
import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.testsupport.ApplicationSharedSpec
import com.cyrelis.srag.domain.ingestionjob.JobStatus
import com.cyrelis.srag.domain.transcript.IngestSource
import zio.*
import zio.test.*

object TextPreparatorPipelineSpec extends ApplicationSharedSpec {

  override def spec =
    suite("TextPreparatorPipeline")(
      test("prepare loads text blob and creates transcript with expected words") {
        val job = sampleJob(
          id = UUID.randomUUID(),
          source = IngestSource.Text,
          status = JobStatus.Transcribing,
          attempt = 1,
          blobKey = Some("text-job-1"),
          metadata = Map("tenant" -> "acme")
        )

        for {
          refs   <- makeTextPreparatorRefs
          result <- (for {
                      pipeline <- ZIO.service[TextPreparatorPipeline]
                      value    <- pipeline.prepare(job)
                    } yield value).provideLayer(
                      textPreparatorLayer(
                        makeTextBlobStore(
                          refs,
                          _ => ZIO.succeed("hello   world\nfrom text".getBytes(StandardCharsets.UTF_8))
                        )
                      )
                    )
          fetched <- refs.fetchedBlobKeys.get
        } yield assertTrue(
          fetched == Vector("text-job-1"),
          result.source == IngestSource.Text,
          result.metadata == Map("tenant" -> "acme"),
          result.words.map(_.text) == List("hello", "world", "from", "text"),
          result.text == "hello world from text"
        )
      },
      test("prepare fails when blob key is missing") {
        val job = sampleJob(
          id = UUID.randomUUID(),
          source = IngestSource.Text,
          status = JobStatus.Transcribing,
          attempt = 1,
          blobKey = None
        )

        for {
          refs   <- makeTextPreparatorRefs
          result <- (for {
                      pipeline <- ZIO.service[TextPreparatorPipeline]
                      value    <- pipeline.prepare(job).either
                    } yield value).provideLayer(
                      textPreparatorLayer(
                        makeTextBlobStore(refs, _ => ZIO.succeed(Array.emptyByteArray))
                      )
                    )
          fetched <- refs.fetchedBlobKeys.get
        } yield assertTrue(
          result == Left(PipelineError.DatabaseError(s"Missing blob key for text job ${job.id}", None)),
          fetched.isEmpty
        )
      }
    ) @@ TestAspect.withLiveClock
}
