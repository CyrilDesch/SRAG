package com.cyrelis.srag.application.usecases.ingestion.pipeline.preparator

import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.testsupport.ApplicationSharedSpec
import com.cyrelis.srag.domain.ingestionjob.JobStatus
import com.cyrelis.srag.domain.transcript.IngestSource
import zio.*
import zio.test.*

object AudioPreparatorPipelineSpec extends ApplicationSharedSpec {

  override def spec =
    suite("AudioPreparatorPipeline")(
      test("prepare fetches blob, transcribes and enriches transcript metadata") {
        val jobId        = UUID.randomUUID()
        val transcriptId = UUID.randomUUID()
        val audio        = Array[Byte](1, 2, 3, 4)
        val job          = sampleJob(
          id = jobId,
          source = IngestSource.Audio,
          status = JobStatus.Transcribing,
          attempt = 1,
          blobKey = Some("audio-job-1"),
          mediaContentType = Some("audio/mpeg"),
          mediaFilename = Some("meeting.mp3"),
          metadata = Map("tenant" -> "acme")
        )
        val transcript = sampleTranscript(
          id = transcriptId,
          source = IngestSource.Audio,
          text = "hello audio",
          metadata = Map("source" -> "asr"),
          confidence = 0.98
        )

        for {
          refs   <- makeAudioPreparatorRefs
          result <- (for {
                      pipeline <- ZIO.service[AudioPreparatorPipeline]
                      value    <- pipeline.prepare(job)
                    } yield value).provideLayer(
                      audioPreparatorLayer(
                        makeAudioBlobStore(refs, _ => ZIO.succeed(audio)),
                        makeAudioTranscriber(refs, (_, _, _) => ZIO.succeed(transcript))
                      )
                    )
          fetched <- refs.fetchedBlobKeys.get
          calls   <- refs.transcribeCalls.get
        } yield assertTrue(
          fetched == Vector("audio-job-1"),
          calls == Vector((audio.toList, "audio/mpeg", "meeting.mp3")),
          result.id == transcriptId,
          result.metadata == Map("source" -> "asr", "tenant" -> "acme"),
          result.text == "hello audio"
        )
      },
      test("prepare fails when media filename is missing") {
        val job = sampleJob(
          id = UUID.randomUUID(),
          source = IngestSource.Audio,
          status = JobStatus.Transcribing,
          attempt = 1,
          blobKey = Some("audio-job-2"),
          mediaContentType = Some("audio/mpeg"),
          mediaFilename = None
        )

        for {
          refs   <- makeAudioPreparatorRefs
          result <- (for {
                      pipeline <- ZIO.service[AudioPreparatorPipeline]
                      value    <- pipeline.prepare(job).either
                    } yield value).provideLayer(
                      audioPreparatorLayer(
                        makeAudioBlobStore(refs, _ => ZIO.succeed(Array.emptyByteArray)),
                        makeAudioTranscriber(refs, (_, _, _) => ZIO.fail(PipelineError.TranscriptionError("unused")))
                      )
                    )
          fetched <- refs.fetchedBlobKeys.get
          calls   <- refs.transcribeCalls.get
        } yield assertTrue(
          result == Left(PipelineError.DatabaseError(s"Missing media filename for job ${job.id}", None)),
          fetched.isEmpty,
          calls.isEmpty
        )
      }
    ) @@ TestAspect.withLiveClock
}
