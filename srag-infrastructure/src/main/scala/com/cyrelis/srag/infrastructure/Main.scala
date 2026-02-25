package com.cyrelis.srag.infrastructure

import com.cyrelis.srag.application.ports.driven.job.JobQueuePort
import com.cyrelis.srag.application.ports.driven.job.JobQueuePort.LockExpirationSeconds
import com.cyrelis.srag.application.ports.driving.HealthCheckPort
import com.cyrelis.srag.application.types.HealthStatus
import com.cyrelis.srag.application.workers.DefaultIngestionJobWorker
import com.cyrelis.srag.infrastructure.adapters.driving.Gateway
import com.cyrelis.srag.infrastructure.config.RuntimeConfig
import com.cyrelis.srag.infrastructure.migration.MigrationRunner
import com.cyrelis.srag.infrastructure.runtime.*
import zio.*

object Main extends ZIOAppDefault {

  type AppDependencies = RuntimeConfig & DatabaseModule.DatabaseEnvironment & DrivenModule.DrivenEnvironment &
    ServiceModule.ServiceEnvironment & DrivingModule.DrivingEnvironment

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> zio.logging.backend.SLF4J.slf4j

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    startup.provide(
      RuntimeConfig.layer
        .tapError(err => ZIO.logError(s"Failed to load configuration: ${err.getMessage}"))
        .orDie,
      DatabaseModule.live,
      DrivenModule.live,
      ServiceModule.live,
      DrivingModule.live
    )

  private def startup: ZIO[AppDependencies, Nothing, Unit] =
    ZIO.scoped {
      for {
        _      <- ZIO.logInfo("Starting SRAG application...")
        _      <- runMigrations
        _      <- ensureAllHealthy
        _      <- recoverAbandonedJobsAfterDelay.forkDaemon
        worker <- ZIO.service[DefaultIngestionJobWorker]
        _      <- ZIO.logInfo("Starting ingestion job worker...")
        _      <- worker.run.forkScoped
        _      <- startGateway
        _      <- ZIO.never
      } yield ()
    }.orDie

  private def recoverAbandonedJobsAfterDelay: ZIO[JobQueuePort, Nothing, Unit] =
    for {
      _ <- ZIO.logInfo(s"Scheduling job recovery in $LockExpirationSeconds seconds (after lock expiration)...")
      _ <- ZIO.sleep(LockExpirationSeconds.seconds)
      _ <- recoverAbandonedJobs
    } yield ()

  private def recoverAbandonedJobs: ZIO[JobQueuePort, Nothing, Unit] =
    for {
      jobQueue       <- ZIO.service[JobQueuePort]
      _              <- ZIO.logInfo("Recovering abandoned jobs from Redis...")
      redisRecovered <-
        jobQueue
          .recoverStaleJobs()
          .catchAll(err => ZIO.logWarning(s"Failed to recover stale jobs from Redis: ${err.message}").as(0))
      _ <- if (redisRecovered > 0) {
             ZIO.logInfo(s"Recovered $redisRecovered job(s) from Redis processing queue")
           } else {
             ZIO.unit
           }
      _ <- ZIO.logInfo("Job recovery completed")
    } yield ()

  private def runMigrations: ZIO[RuntimeConfig, Throwable, Unit] =
    for {
      config <- ZIO.service[RuntimeConfig]
      _      <- if (config.migrations.runOnStartup) {
             MigrationRunner.runAll().catchAll { error =>
               if (config.migrations.failOnError) {
                 ZIO.logError(s"Migration failed and fail-on-error is enabled: ${error.getMessage}") *>
                   ZIO.fail(error)
               } else {
                 ZIO.logWarning(s"Migration failed but continuing (fail-on-error is disabled): ${error.getMessage}")
               }
             }
           } else {
             ZIO.logInfo("Skipping migrations (run-on-startup is disabled)")
           }
    } yield ()

  private def ensureAllHealthy: ZIO[HealthCheckPort & RuntimeConfig, Throwable, Unit] =
    for {
      healthCheckPort <- ZIO.service[HealthCheckPort]
      _               <- ZIO.logInfo("Running health checks...")
      attempt          = for {
                  results <- healthCheckPort.checkAllServices()
                  _       <- logHealthResults(results)
                  hasBad   = results.exists {
                             case com.cyrelis.srag.application.types.HealthStatus.Healthy(_, _, _) => false
                             case _                                                                => true
                           }
                  _ <- ZIO.when(hasBad)(
                         ZIO.fail(new RuntimeException("Unhealthy dependencies detected. Aborting startup."))
                       )
                } yield ()
      initial  = zio.Duration.fromMillis(2000)
      maxDelay = zio.Duration.fromMillis(10000)
      schedule = Schedule
                   .exponential(initial, 2.0)
                   .modifyDelay((_, d) => if (d > maxDelay) maxDelay else d)
                   .&&(Schedule.recurs(3))
      _ <- attempt.retry(schedule).catchAll { err =>
             ZIO.logError(err.getMessage) *> ZIO.fail(err)
           }
      _ <- ZIO.logInfo("All systems operational")
    } yield ()

  private def logHealthResults(results: List[HealthStatus]): ZIO[Any, Throwable, Unit] =
    ZIO
      .foreach(results) { status =>
        status match {
          case HealthStatus.Healthy(serviceName, _, _) =>
            ZIO.logInfo(s"✓ $serviceName")
          case HealthStatus.Unhealthy(serviceName, _, error, _) =>
            ZIO.logWarning(s"✗ $serviceName: $error")
          case HealthStatus.Timeout(serviceName, _, timeoutMs) =>
            ZIO.logWarning(s"✗ $serviceName: timeout after ${timeoutMs}ms")
        }
      }
      .unit

  private def startGateway: ZIO[Scope & AppDependencies, Throwable, Unit] =
    for {
      gateway <- ZIO.service[Gateway]
      _       <- gateway match {
             case restGateway: com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.IngestRestGateway =>
               restGateway.startWithDeps
             case _ =>
               gateway.start
           }
    } yield ()
}
