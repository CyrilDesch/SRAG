package com.cyrelis.srag.application.model.query

final case class RerankerResult(
  candidate: RerankerCandidate,
  score: Double
)
