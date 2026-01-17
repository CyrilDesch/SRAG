package com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.test

import java.io.File

import io.circe.Codec
import sttp.model.Part

final case class TestTranscriberRestDto(file: Part[File])

final case class TestTranscriberResultRestDto(result: String) derives Codec
