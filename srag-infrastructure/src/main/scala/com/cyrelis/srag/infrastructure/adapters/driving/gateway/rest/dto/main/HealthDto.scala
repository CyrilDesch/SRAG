package com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.main

import com.cyrelis.srag.application.types.HealthStatus
import io.circe.Codec

final case class HealthStatusRestDto(
  status: String,
  serviceName: String,
  checkedAt: String,
  error: Option[String] = None,
  timeoutMs: Option[Long] = None,
  details: Map[String, String] = Map.empty
) derives Codec

object HealthStatusRestDto {
  def fromDomain(healthStatus: HealthStatus): HealthStatusRestDto = healthStatus match {
    case HealthStatus.Healthy(serviceName, checkedAt, details) =>
      HealthStatusRestDto(
        status = "healthy",
        serviceName = serviceName,
        checkedAt = checkedAt.toString,
        details = details
      )
    case HealthStatus.Unhealthy(serviceName, checkedAt, error, details) =>
      HealthStatusRestDto(
        status = "unhealthy",
        serviceName = serviceName,
        checkedAt = checkedAt.toString,
        error = Some(error),
        details = details
      )
    case HealthStatus.Timeout(serviceName, checkedAt, timeoutMs) =>
      HealthStatusRestDto(
        status = "timeout",
        serviceName = serviceName,
        checkedAt = checkedAt.toString,
        timeoutMs = Some(timeoutMs)
      )
  }
}
