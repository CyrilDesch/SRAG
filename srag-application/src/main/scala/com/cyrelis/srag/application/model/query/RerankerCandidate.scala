package com.cyrelis.srag.application.model.query

import java.util.UUID

final case class RerankerCandidate(
  transcriptId: UUID,
  segmentIndex: Int,
  text: String
)
