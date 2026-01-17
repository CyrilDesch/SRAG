package com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.test

import io.circe.Codec

final case class TestDocumentParserRestDto(content: String) derives Codec
