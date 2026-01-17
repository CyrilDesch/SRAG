package com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.test

import io.circe.Codec

final case class TestVectorStoreRestDto(
  text: String
) derives Codec

final case class TestVectorStoreQueryRestDto(
  text: String
) derives Codec

final case class VectorSearchResultDto(
  transcriptId: String,
  segmentIndex: Int,
  score: Double,
  transcriptText: Option[String] = None
) derives Codec

final case class TestVectorStoreQueryResultRestDto(
  results: List[VectorSearchResultDto],
  totalResults: Int
) derives Codec
