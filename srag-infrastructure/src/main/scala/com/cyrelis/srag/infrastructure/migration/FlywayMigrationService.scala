package com.cyrelis.srag.infrastructure.migration

import javax.sql.DataSource

import org.flywaydb.core.Flyway
import zio.*

trait MigrationService {
  def runMigrations(): Task[Unit]
}

final class FlywayMigrationService(dataSource: DataSource) extends MigrationService {

  override def runMigrations(): Task[Unit] =
    ZIO.attempt {
      val flyway = Flyway
        .configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .baselineOnMigrate(true)
        .load()

      val migrationsApplied = flyway.migrate()

      migrationsApplied.migrationsExecuted
    }.flatMap { count =>
      if (count > 0) {
        ZIO.logInfo(s"Applied $count database migration(s)")
      } else {
        ZIO.logInfo("Database schema is up to date")
      }
    }.catchAll { error =>
      ZIO.logError(s"Database migration failed: ${error.getMessage}") *>
        ZIO.fail(error)
    }
}
