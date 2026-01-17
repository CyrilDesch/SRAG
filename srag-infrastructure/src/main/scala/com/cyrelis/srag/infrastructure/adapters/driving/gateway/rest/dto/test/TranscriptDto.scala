package com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.test

import com.cyrelis.srag.domain.transcript.{Transcript, Word}
import com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.common.IngestSourceDto
import io.circe.Codec

final case class WordDto(
  text: String,
  start: Long,
  end: Long,
  confidence: Double
) derives Codec

object WordDto {
  def fromDomain(word: Word): WordDto =
    WordDto(
      text = word.text,
      start = word.start,
      end = word.end,
      confidence = word.confidence
    )
}

final case class TranscriptResponseDto(
  id: String,
  words: List[WordDto],
  createdAt: String,
  source: IngestSourceDto,
  language: Option[String],
  metadata: Map[String, String]
) derives Codec

object TranscriptResponseDto {
  def fromDomain(transcript: Transcript): TranscriptResponseDto =
    TranscriptResponseDto(
      id = transcript.id.toString,
      words = transcript.words.map(WordDto.fromDomain),
      createdAt = transcript.createdAt.toString,
      source = IngestSourceDto.fromDomain(transcript.source),
      language = transcript.language.map(_.value),
      metadata = transcript.metadata
    )
}
