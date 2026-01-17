package com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.test

import io.circe.Codec

final case class TestRerankerRestDto(
  query: String,
  candidates: List[String],
  topK: Int
) derives Codec

final case class RerankerResultDto(
  text: String,
  score: Double
) derives Codec

final case class TestRerankerResultRestDto(
  results: List[RerankerResultDto]
) derives Codec
