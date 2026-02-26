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
        _               <- ZIO.logDebug(s"Job ${job.id} - media: $mediaFilename (content-type: $contentType)")
        audio           <- blobStore.fetchAudio(blobKey)
        _               <- ZIO.logDebug(s"Job ${job.id} - transcribing audio (size: ${audio.length} bytes)")
        temp_transcript <- transcriber.transcribe(audio, contentType, mediaFilename)
        _               <-
          ZIO.logDebug(
            s"Job ${job.id} - transcription completed: transcript ${temp_transcript.id}, text length: ${temp_transcript.text.length} chars"
          )
        transcript = temp_transcript.addMetadatas(job.metadata)
      } yield transcript
  }
}
