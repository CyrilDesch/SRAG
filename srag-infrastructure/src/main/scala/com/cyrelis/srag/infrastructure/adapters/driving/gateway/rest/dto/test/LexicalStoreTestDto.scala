package com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.test

import io.circe.Codec

final case class TestLexicalStoreIndexRestDto(
  segments: List[String],
  metadata: Map[String, String] = Map.empty
) derives Codec

final case class TestLexicalStoreQueryRestDto(
  queryText: String,
  limit: Int
) derives Codec

final case class LexicalSearchResultDto(
  text: String,
  score: Double
) derives Codec

final case class TestLexicalStoreQueryResultRestDto(
  results: List[LexicalSearchResultDto],
  totalResults: Int
) derives Codec
