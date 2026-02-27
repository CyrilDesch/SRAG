package com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.common

import com.cyrelis.srag.domain.transcript.IngestSource
import io.circe.{Codec, Decoder, Encoder}
import sttp.tapir.Schema

enum IngestSourceDto:
  case Audio, Text

object IngestSourceDto {
  given Codec[IngestSourceDto] = Codec.from(
    Decoder.decodeString.emap {
      case "Audio" => Right(IngestSourceDto.Audio)
      case "Text"  => Right(IngestSourceDto.Text)
      case other   => Left(s"Unknown IngestSourceDto: $other")
    },
    Encoder.encodeString.contramap(_.toString)
  )

  given Schema[IngestSourceDto] = Schema.derivedEnumeration[IngestSourceDto].defaultStringBased

  def toDomain(source: IngestSourceDto): IngestSource =
    source match
      case IngestSourceDto.Audio => IngestSource.Audio
      case IngestSourceDto.Text  => IngestSource.Text

  def fromDomain(source: IngestSource): IngestSourceDto =
    source match
      case IngestSource.Audio => IngestSourceDto.Audio
      case IngestSource.Text  => IngestSourceDto.Text
}
