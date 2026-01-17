package com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.test

import io.circe.Codec

final case class TestEmbedderRestDto(content: String) derives Codec

final case class EmbeddingChunkDto(
  chunk: String,
  dimensions: Int,
  embedding: List[Float]
) derives Codec

final case class TestEmbedderResultRestDto(
  totalChunks: Int,
  chunks: List[EmbeddingChunkDto]
) derives Codec
