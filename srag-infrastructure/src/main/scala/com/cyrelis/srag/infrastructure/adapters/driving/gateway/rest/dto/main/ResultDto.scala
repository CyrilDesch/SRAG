package com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.main

import com.cyrelis.srag.domain.ingestionjob.IngestionJob
import io.circe.Codec

final case class JobAcceptedRestDto(
  jobId: String,
  status: String
) derives Codec

object JobAcceptedRestDto {
  def fromDomain(job: IngestionJob): JobAcceptedRestDto =
    JobAcceptedRestDto(
      jobId = job.id.toString,
      status = job.status.toString
    )
}

final case class JobStatusRestDto(
  jobId: String,
  transcriptId: Option[String],
  status: String,
  attempt: Int,
  maxAttempts: Int,
  errorMessage: Option[String],
  createdAt: String,
  updatedAt: String,
  source: Option[String] = None,
  metadata: Option[Map[String, String]] = None
) derives Codec

object JobStatusRestDto {
  def fromDomain(job: IngestionJob): JobStatusRestDto =
    JobStatusRestDto(
      jobId = job.id.toString,
      transcriptId = job.transcriptId.map(_.toString),
      status = job.status.toString,
      attempt = job.attempt,
      maxAttempts = job.maxAttempts,
      errorMessage = job.errorMessage,
      createdAt = job.createdAt.toString,
      updatedAt = job.updatedAt.toString,
      source = Some(job.source.toString),
      metadata = Some(job.metadata)
    )
}
