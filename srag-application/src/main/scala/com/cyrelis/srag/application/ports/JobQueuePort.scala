package com.cyrelis.srag.application.ports

import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.model.healthcheck.HealthStatus
import zio.*

trait JobQueuePort {
  def enqueue(jobId: UUID): ZIO[Any, PipelineError, Unit]
  def dequeueBatch(max: Int): ZIO[Any, PipelineError, List[UUID]]
  def claim(blockingTimeoutSec: Int): ZIO[Any, PipelineError, Option[UUID]]
  def heartbeat(jobId: UUID): ZIO[Any, PipelineError, Unit]
  def ack(jobId: UUID): ZIO[Any, PipelineError, Unit]
  def release(jobId: UUID): ZIO[Any, PipelineError, Unit]
  def recoverStaleJobs(): ZIO[Any, PipelineError, Int]
  def retry(jobId: UUID, attempt: Int, delay: zio.Duration): ZIO[Any, PipelineError, Unit]
  def deadLetter(jobId: UUID, reason: String): ZIO[Any, PipelineError, Unit]
  def healthCheck(): Task[HealthStatus]
}

object JobQueuePort {

  /**
   * Lock expiration time in seconds for distributed job locking. Locks are
   * renewed by heartbeat every 30 seconds, so expiration at 60s provides safety
   * margin. If worker crashes, lock expires after 1 minute for quick recovery.
   */
  val LockExpirationSeconds: Int = 60
}
