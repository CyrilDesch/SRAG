package com.cyrelis.srag.application.ports

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.model.healthcheck.HealthStatus
import com.cyrelis.srag.application.model.query.{RerankerCandidate, RerankerResult}
import zio.*

trait RerankerPort {
  def rerank(
    query: String,
    candidates: List[RerankerCandidate],
    topK: Int
  ): ZIO[Any, PipelineError, List[RerankerResult]]

  def healthCheck(): Task[HealthStatus]
}
