package com.cyrelis.srag.domain.transcript

final case class Word(
  text: String,
  start: Long,
  end: Long,
  confidence: Double
)
