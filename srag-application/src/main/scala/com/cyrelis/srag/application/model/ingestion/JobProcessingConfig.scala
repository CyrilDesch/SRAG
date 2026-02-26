package com.cyrelis.srag.application.model.ingestion

import scala.concurrent.duration.FiniteDuration

final case class JobProcessingConfig(
  maxAttempts: Int,
  pollInterval: FiniteDuration,
  batchSize: Int,
  initialRetryDelay: FiniteDuration,
  maxRetryDelay: FiniteDuration,
  backoffFactor: Double,
  maxConcurrentJobs: Int = 1
)
