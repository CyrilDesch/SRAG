package com.cyrelis.srag.infrastructure.adapters.driven.queue

import java.util.UUID

import scala.jdk.CollectionConverters.*

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.model.healthcheck.HealthStatus
import com.cyrelis.srag.application.ports.JobQueuePort
import com.cyrelis.srag.infrastructure.config.JobQueueAdapterConfig
import redis.clients.jedis.args.ListDirection
import redis.clients.jedis.params.SetParams
import redis.clients.jedis.{DefaultJedisClientConfig, RedisClient}
import zio.*

private final class RedisJobQueueAdapter(
  redis: RedisClient,
  queueKey: String,
  processingKey: String,
  lockKeyPrefix: String,
  deadLetterKey: String,
  lockExpirationSeconds: Int
) extends JobQueuePort {

  private def lockKey(jobId: UUID): String = s"$lockKeyPrefix:${jobId.toString}"

  override def enqueue(jobId: UUID): ZIO[Any, PipelineError, Unit] =
    ZIO.attemptBlocking {
      redis.lrem(processingKey, 0, jobId.toString)
      redis.rpush(queueKey, jobId.toString)
    }.unit.mapError(e => PipelineError.QueueError("Redis enqueue failed", Some(e)))

  override def dequeueBatch(max: Int): ZIO[Any, PipelineError, List[UUID]] =
    ZIO.attemptBlocking {
      val popped = redis.lpop(queueKey, max)
      if (popped == null) List.empty
      else popped.asScala.iterator.map(UUID.fromString).toList
    }.mapError(e => PipelineError.QueueError("Redis dequeue failed", Some(e)))

  override def claim(blockingTimeoutSec: Int): ZIO[Any, PipelineError, Option[UUID]] =
    ZIO.blocking {
      ZIO.attemptBlocking {
        val result = redis.blmove(
          queueKey,
          processingKey,
          ListDirection.RIGHT,
          ListDirection.LEFT,
          blockingTimeoutSec.toDouble
        )
        if (result == null || result.isEmpty) {
          None
        } else {
          val jobId        = UUID.fromString(result)
          val lockAcquired = redis.set(
            lockKey(jobId),
            "locked",
            SetParams.setParams().nx().ex(lockExpirationSeconds)
          )
          if (lockAcquired != null && lockAcquired.equalsIgnoreCase("OK")) {
            Some(jobId)
          } else {
            redis.lrem(processingKey, 0, result)
            None
          }
        }
      }
    }.mapError(e => PipelineError.QueueError("Redis claim failed", Some(e)))

  override def heartbeat(jobId: UUID): ZIO[Any, PipelineError, Unit] =
    ZIO.attemptBlocking {
      // Renew lock expiration - only if lock still exists and belongs to us
      val lockKeyStr = lockKey(jobId)
      redis.expire(lockKeyStr, lockExpirationSeconds)
    }.unit.mapError(e => PipelineError.QueueError("Redis heartbeat failed", Some(e)))

  override def ack(jobId: UUID): ZIO[Any, PipelineError, Unit] =
    ZIO.attemptBlocking {
      // Remove from processing queue and release lock
      redis.lrem(processingKey, 0, jobId.toString)
      redis.del(lockKey(jobId))
    }.unit.mapError(e => PipelineError.QueueError("Redis ack failed", Some(e)))

  override def release(jobId: UUID): ZIO[Any, PipelineError, Unit] =
    ZIO.attemptBlocking {
      // Remove from processing queue and release lock, then re-enqueue
      redis.lrem(processingKey, 0, jobId.toString)
      redis.del(lockKey(jobId))
      redis.rpush(queueKey, jobId.toString)
    }.unit.mapError(e => PipelineError.QueueError("Redis release failed", Some(e)))

  override def recoverStaleJobs(): ZIO[Any, PipelineError, Int] =
    ZIO.attemptBlocking {
      val processingJobs = redis.lrange(processingKey, 0, -1)
      val totalJobs      = processingJobs.size()
      val mainQueueSize  = redis.llen(queueKey).intValue()

      println(
        s"[Redis] recoverStaleJobs: found ${totalJobs} job(s) in processing queue, ${mainQueueSize} job(s) in main queue"
      )

      var recovered = 0

      // Use Lua script for atomic recovery (prevents race conditions with multiple backends)
      // Script: if lock doesn't exist AND job is in processing queue, move it to main queue
      val recoverScript =
        s"""
           |local processingKey = KEYS[1]
           |local queueKey = KEYS[2]
           |local lockKeyPrefix = KEYS[3]
           |local jobId = ARGV[1]
           |local lockKey = lockKeyPrefix .. ':' .. jobId
           |
           |-- Check if lock exists
           |if redis.call('EXISTS', lockKey) == 0 then
           |  -- Lock doesn't exist, try to atomically move from processing to queue
           |  local removed = redis.call('LREM', processingKey, 1, jobId)
           |  if removed > 0 then
           |    redis.call('RPUSH', queueKey, jobId)
           |    return 1
           |  end
           |end
           |return 0
           |""".stripMargin

      processingJobs.asScala.foreach { jobIdStr =>
        val jobId      = UUID.fromString(jobIdStr)
        val lockKeyStr = lockKey(jobId)
        val lockExists = redis.exists(lockKeyStr)

        println(s"[Redis] Checking job $jobIdStr: lock exists = $lockExists")

        val result = redis.eval(
          recoverScript,
          java.util.Arrays.asList(
            processingKey,
            queueKey,
            lockKeyPrefix
          ),
          java.util.Arrays.asList(jobIdStr)
        )
        if (result != null && result.asInstanceOf[java.lang.Long] == 1) {
          recovered += 1
          println(s"[Redis] Recovered job $jobIdStr from processing queue to main queue")
        } else {
          println(s"[Redis] Job $jobIdStr not recovered (lock still exists or already processed)")
        }
      }

      println(s"[Redis] recoverStaleJobs completed: recovered $recovered out of $totalJobs job(s)")
      recovered
    }
      .tapBoth(
        error => ZIO.logError(s"Failed to recover stale jobs: ${error.getMessage}"),
        count => ZIO.logInfo(s"Recovered $count stale job(s) from Redis processing queue")
      )
      .mapError(e => PipelineError.QueueError("Redis recoverStaleJobs failed", Some(e)))

  override def retry(jobId: UUID, attempt: Int, delay: Duration): ZIO[Any, PipelineError, Unit] =
    release(jobId)

  override def deadLetter(jobId: UUID, reason: String): ZIO[Any, PipelineError, Unit] =
    ZIO.attemptBlocking {
      redis.lrem(processingKey, 0, jobId.toString)
      redis.del(lockKey(jobId))
      redis.rpush(deadLetterKey, s"${jobId.toString}:$reason")
    }.unit.mapError(e => PipelineError.QueueError("Redis deadLetter failed", Some(e)))

  override def healthCheck(): Task[HealthStatus] =
    ZIO.attemptBlocking {
      val pong = redis.ping()
      if (pong != null && pong.equalsIgnoreCase("PONG"))
        HealthStatus.Healthy("JobQueuePort", java.time.Instant.now(), Map("ping" -> "PONG"))
      else
        HealthStatus.Unhealthy(
          "JobQueuePort",
          java.time.Instant.now(),
          s"Unexpected PING response: ${Option(pong).getOrElse("null")}"
        )
    }.catchAll(e => ZIO.succeed(HealthStatus.Unhealthy("JobQueuePort", java.time.Instant.now(), e.getMessage)))
}

object RedisJobQueueAdapter {

  val layer: ZLayer[JobQueueAdapterConfig.Redis, Throwable, JobQueuePort] =
    ZLayer.scoped {
      for {
        config <- ZIO.service[JobQueueAdapterConfig.Redis]
        client <- ZIO.acquireRelease(
                    ZIO.attemptBlocking {
                      val clientCfg = DefaultJedisClientConfig
                        .builder()
                        .password(config.password.orNull)
                        .database(config.database)
                        .build()

                      RedisClient
                        .builder()
                        .hostAndPort(config.host, config.port)
                        .clientConfig(clientCfg)
                        .build()
                    }
                  )((client: RedisClient) => ZIO.attempt(client.close()).ignore)
      } yield new RedisJobQueueAdapter(
        redis = client,
        queueKey = config.queueKey,
        processingKey = s"${config.queueKey}:processing",
        lockKeyPrefix = s"${config.queueKey}:lock",
        deadLetterKey = config.deadLetterKey,
        lockExpirationSeconds = JobQueuePort.LockExpirationSeconds
      )
    }
}
