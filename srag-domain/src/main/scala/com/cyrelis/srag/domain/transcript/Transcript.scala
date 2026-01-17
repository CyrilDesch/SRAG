package com.cyrelis.srag.domain.transcript

import java.time.Instant
import java.util.UUID

import com.cyrelis.srag.domain.transcript.{IngestSource, LanguageCode, Word}

final case class Transcript(
  id: UUID,
  language: Option[LanguageCode],
  words: List[Word],
  confidence: Double,
  createdAt: Instant,
  source: IngestSource,
  metadata: Map[String, String]
):
  def addMetadatas(extra: Map[String, String]): Transcript =
    copy(metadata = metadata ++ extra)

  def addMetadata(key: String, value: String): Transcript =
    copy(metadata = metadata + (key -> value))

  def text: String =
    words.map(_.text).mkString(" ")
