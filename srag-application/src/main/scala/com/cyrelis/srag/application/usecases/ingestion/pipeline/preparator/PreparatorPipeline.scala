package com.cyrelis.srag.application.usecases.ingestion.pipeline.preparator

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.domain.ingestionjob.IngestionJob
import com.cyrelis.srag.domain.transcript.Transcript
import zio.ZIO

trait PreparatorPipeline {
  def prepare(job: IngestionJob): ZIO[Any, PipelineError, Transcript]
}
