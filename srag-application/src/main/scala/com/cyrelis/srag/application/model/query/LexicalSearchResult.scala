package com.cyrelis.srag.application.model.query

import java.util.UUID

final case class LexicalSearchResult(
  transcriptId: UUID,
  segmentIndex: Int,
  score: Double,
  text: String,
  metadata: Map[String, String]
)
