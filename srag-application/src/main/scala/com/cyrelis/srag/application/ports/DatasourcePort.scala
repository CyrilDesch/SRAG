package com.cyrelis.srag.application.ports

import com.cyrelis.srag.application.model.healthcheck.HealthStatus
import zio.*

trait DatasourcePort {
  def healthCheck(): Task[HealthStatus]
}
