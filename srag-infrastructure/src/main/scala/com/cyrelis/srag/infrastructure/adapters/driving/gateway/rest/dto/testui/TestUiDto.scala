package com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.testui

import io.circe.Codec

final case class AdminJobDto(
  jobId: String,
  transcriptId: Option[String],
  status: String,
  attempt: Int,
  maxAttempts: Int,
  errorMessage: Option[String],
  source: Option[String],
  createdAt: String,
  updatedAt: String,
  metadata: Option[Map[String, String]]
) derives Codec

final case class AdminBlobDto(
  key: String,
  filename: Option[String],
  contentType: Option[String],
  size: Option[Long],
  created: Option[String]
) derives Codec

final case class AdminVectorDto(
  id: String,
  transcriptId: Option[String],
  segmentIndex: Option[Int],
  vector: Option[List[Float]],
  payload: Option[Map[String, String]]
) derives Codec

final case class AdminVectorsResponse(
  total: Int,
  vectors: List[AdminVectorDto]
) derives Codec

final case class AdminOpenSearchDocument(
  id: String,
  transcriptId: Option[String],
  segmentIndex: Option[Int],
  text: Option[String],
  metadata: Option[Map[String, String]]
) derives Codec

final case class AdminOpenSearchResponse(
  total: Int,
  documents: List[AdminOpenSearchDocument]
) derives Codec
