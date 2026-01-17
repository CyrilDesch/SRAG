package com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.main

import com.cyrelis.srag.application.types.ContextSegment
import io.circe.Codec

final case class QueryResponseDto(
  transcriptId: String,
  segmentIndex: Int,
  text: String,
  score: Double
) derives Codec

object QueryResponseDto {
  def fromDomain(segment: ContextSegment): QueryResponseDto =
    QueryResponseDto(
      transcriptId = segment.transcriptId.toString,
      segmentIndex = segment.segmentIndex,
      text = segment.text,
      score = segment.score
    )
}
