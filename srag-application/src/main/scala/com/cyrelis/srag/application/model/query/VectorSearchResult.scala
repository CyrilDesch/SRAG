package com.cyrelis.srag.application.model.query

import java.util.UUID

final case class VectorSearchResult(
  transcriptId: UUID,
  segmentIndex: Int,
  score: Double
)
