package com.cyrelis.srag.application.model.query

import java.util.UUID

final case class ContextSegment(
  transcriptId: UUID,
  segmentIndex: Int,
  text: String,
  score: Double
)
