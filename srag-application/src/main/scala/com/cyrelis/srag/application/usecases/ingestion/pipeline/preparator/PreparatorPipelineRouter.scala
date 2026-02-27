package com.cyrelis.srag.application.usecases.ingestion.pipeline.preparator

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.usecases.ingestion.pipeline.preparator.{
  AudioPreparatorPipeline,
  TextPreparatorPipeline
}
import com.cyrelis.srag.domain.ingestionjob.IngestionJob
import com.cyrelis.srag.domain.transcript.{IngestSource, Transcript}
import zio.*

trait PreparatorRouterPipeline {
  def dispatchAndPrepare(job: IngestionJob): ZIO[Any, PipelineError, Transcript]
}

object PreparatorRouterPipeline {
  val layer: ZLayer[AudioPreparatorPipeline & TextPreparatorPipeline, Nothing, PreparatorRouterPipeline] =
    ZLayer {
      for {
        audioPreparator <- ZIO.service[AudioPreparatorPipeline]
        textPreparator  <- ZIO.service[TextPreparatorPipeline]
      } yield new PreparatorRouterPipelineLive(audioPreparator, textPreparator)
    }

  private final class PreparatorRouterPipelineLive(
    audioPreparator: AudioPreparatorPipeline,
    textPreparator: TextPreparatorPipeline
  ) extends PreparatorRouterPipeline {

    override def dispatchAndPrepare(job: IngestionJob): ZIO[Any, PipelineError, Transcript] =
      job.source match {
        case IngestSource.Audio => audioPreparator.prepare(job)
        case IngestSource.Text  => textPreparator.prepare(job)
      }
  }
}
