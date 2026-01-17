package com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.test

import com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.common.IngestSourceDto
import io.circe.Codec

final case class TestDatabaseRestDto(
  text: String,
  source: IngestSourceDto
) derives Codec
