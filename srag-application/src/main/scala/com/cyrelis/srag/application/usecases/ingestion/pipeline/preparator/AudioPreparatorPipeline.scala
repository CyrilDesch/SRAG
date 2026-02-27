package com.cyrelis.srag.application.usecases.ingestion.pipeline.preparator

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.ports.{BlobStorePort, TranscriberPort}
import com.cyrelis.srag.domain.ingestionjob.IngestionJob
import com.cyrelis.srag.domain.transcript.Transcript
import zio.*

trait AudioPreparatorPipeline extends PreparatorPipeline

object AudioPreparatorPipeline {
  val layer: ZLayer[BlobStorePort & TranscriberPort, Nothing, AudioPreparatorPipeline] =
    ZLayer {
      for {
        blobStore   <- ZIO.service[BlobStorePort]
        transcriber <- ZIO.service[TranscriberPort]
      } yield new AudioPreparatorPipelineLive(blobStore, transcriber)
    }

  private final class AudioPreparatorPipelineLive(
    blobStore: BlobStorePort,
    transcriber: TranscriberPort
  ) extends AudioPreparatorPipeline {

    private val blobFetchTimeout  = 15.seconds
    private val transcribeTimeout = 300.seconds

    private val blobFetchRetrySchedule =
      Schedule.exponential(100.millis, 2.0).modifyDelay((_, d) => if (d > 5.seconds) 5.seconds else d) &&
        Schedule.recurs(2)

    private val transcribeRetrySchedule =
      Schedule.exponential(200.millis, 2.0).modifyDelay((_, d) => if (d > 5.seconds) 5.seconds else d) &&
        Schedule.recurs(1)

    override def prepare(job: IngestionJob): ZIO[Any, PipelineError, Transcript] =
      for {
        blobKey <- ZIO
                     .fromOption(job.blobKey)
                     .orElseFail(PipelineError.DatabaseError(s"Missing blob key for job ${job.id}", None))
        _           <- ZIO.logDebug(s"Job ${job.id} - fetching blob $blobKey")
        contentType <-
          ZIO
            .fromOption(job.mediaContentType)
            .orElseFail(PipelineError.DatabaseError(s"Missing media content type for job ${job.id}", None))
        mediaFilename <-
          ZIO
            .fromOption(job.mediaFilename)
            .orElseFail(PipelineError.DatabaseError(s"Missing media filename for job ${job.id}", None))
        _     <- ZIO.logDebug(s"Job ${job.id} - media: $mediaFilename (content-type: $contentType)")
        audio <- blobStore
                   .fetchAudio(blobKey)
                   .timeoutFail(
                     PipelineError.TimeoutError(
                       operation = s"audio_preparation.fetch_blob.${job.id}",
                       timeoutMs = blobFetchTimeout.toMillis
                     )
                   )(blobFetchTimeout)
                   .retry(blobFetchRetrySchedule)
        _               <- ZIO.logDebug(s"Job ${job.id} - transcribing audio (size: ${audio.length} bytes)")
        temp_transcript <- transcriber
                             .transcribe(audio, contentType, mediaFilename)
                             .timeoutFail(
                               PipelineError.TimeoutError(
                                 operation = s"audio_preparation.transcribe.${job.id}",
                                 timeoutMs = transcribeTimeout.toMillis
                               )
                             )(transcribeTimeout)
                             .retry(transcribeRetrySchedule)
        _ <-
          ZIO.logDebug(
            s"Job ${job.id} - transcription completed: transcript ${temp_transcript.id}, text length: ${temp_transcript.text.length} chars"
          )
        transcript = temp_transcript.addMetadatas(job.metadata)
      } yield transcript
  }
}
