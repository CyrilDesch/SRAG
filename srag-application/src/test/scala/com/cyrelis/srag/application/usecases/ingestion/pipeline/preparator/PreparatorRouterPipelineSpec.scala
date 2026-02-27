package com.cyrelis.srag.application.usecases.ingestion.pipeline.preparator

import java.util.UUID

import com.cyrelis.srag.application.testsupport.ApplicationSharedSpec
import com.cyrelis.srag.domain.ingestionjob.JobStatus
import com.cyrelis.srag.domain.transcript.IngestSource
import zio.*
import zio.test.*

object PreparatorRouterPipelineSpec extends ApplicationSharedSpec {

  override def spec =
    suite("PreparatorRouterPipeline")(
      test("dispatchAndPrepare uses audio preparator for audio jobs") {
        val job = sampleJob(
          id = UUID.randomUUID(),
          source = IngestSource.Audio,
          status = JobStatus.Transcribing,
          blobKey = Some("audio-job")
        )
        val audioTranscript = sampleTranscript(source = IngestSource.Audio, text = "audio transcript")
        val textTranscript  = sampleTranscript(source = IngestSource.Text, text = "text transcript")

        for {
          refs   <- makeRouterRefs
          result <- (for {
                      router <- ZIO.service[PreparatorRouterPipeline]
                      value  <- router.dispatchAndPrepare(job)
                    } yield value).provideLayer(
                      routerLayer(
                        makeAudioPreparator(refs, _ => ZIO.succeed(audioTranscript)),
                        makeTextPreparator(refs, _ => ZIO.succeed(textTranscript))
                      )
                    )
          audioCalls <- refs.audioCalls.get
          textCalls  <- refs.textCalls.get
        } yield assertTrue(
          result == audioTranscript,
          audioCalls == Vector(job.id),
          textCalls.isEmpty
        )
      },
      test("dispatchAndPrepare uses text preparator for text jobs") {
        val job = sampleJob(
          id = UUID.randomUUID(),
          source = IngestSource.Text,
          status = JobStatus.Transcribing,
          blobKey = Some("text-job")
        )
        val audioTranscript = sampleTranscript(source = IngestSource.Audio, text = "audio transcript")
        val textTranscript  = sampleTranscript(source = IngestSource.Text, text = "text transcript")

        for {
          refs   <- makeRouterRefs
          result <- (for {
                      router <- ZIO.service[PreparatorRouterPipeline]
                      value  <- router.dispatchAndPrepare(job)
                    } yield value).provideLayer(
                      routerLayer(
                        makeAudioPreparator(refs, _ => ZIO.succeed(audioTranscript)),
                        makeTextPreparator(refs, _ => ZIO.succeed(textTranscript))
                      )
                    )
          audioCalls <- refs.audioCalls.get
          textCalls  <- refs.textCalls.get
        } yield assertTrue(
          result == textTranscript,
          audioCalls.isEmpty,
          textCalls == Vector(job.id)
        )
      }
    ) @@ TestAspect.withLiveClock
}
