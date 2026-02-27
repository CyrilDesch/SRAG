package com.cyrelis.srag.application.model.healthcheck

import java.time.Instant

enum HealthStatus:
  case Healthy(
    serviceName: String,
    checkedAt: Instant,
    details: Map[String, String] = Map.empty
  )

  case Unhealthy(
    serviceName: String,
    checkedAt: Instant,
    error: String,
    details: Map[String, String] = Map.empty
  )

  case Timeout(
    serviceName: String,
    checkedAt: Instant,
    timeoutMs: Long
  )
