package com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.main

import java.io.File

import io.circe.Codec
import sttp.model.Part

final case class AudioIngestMultipartDto(file: Part[File], metadata: Map[String, String])

final case class TextIngestRestDto(content: String) derives Codec

final case class DocumentIngestRestDto(content: String, mediaType: String) derives Codec
