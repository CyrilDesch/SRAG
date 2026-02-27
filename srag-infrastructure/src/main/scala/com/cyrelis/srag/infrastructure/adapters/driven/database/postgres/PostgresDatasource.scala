package com.cyrelis.srag.infrastructure.adapters.driven.database.postgres

import java.time.Instant

import com.cyrelis.srag.application.model.healthcheck.HealthStatus
import com.cyrelis.srag.application.ports.DatasourcePort
import com.cyrelis.srag.infrastructure.config.DatabaseAdapterConfig
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.*

final case class HealthCheckResult(result: Int)

trait QuillDatasource extends DatasourcePort {
  def dataSource: javax.sql.DataSource
  def quillContext: Quill.Postgres[SnakeCase]
}

object PostgresDatasource {
  val layer: ZLayer[DatabaseAdapterConfig.Postgres, Throwable, DatasourcePort] =
    ZLayer.scoped {
      for {
        config     <- ZIO.service[DatabaseAdapterConfig.Postgres]
        dataSource <- ZIO.succeed {
                        val ds = new org.postgresql.ds.PGSimpleDataSource()
                        ds.setUrl(s"jdbc:postgresql://${config.host}:${config.port}/${config.database}")
                        ds.setUser(config.user)
                        ds.setPassword(config.password)
                        ds
                      }
        quillContext <- ZIO.fromAutoCloseable(
                          ZIO.succeed(
                            new Quill.Postgres[SnakeCase](SnakeCase, dataSource)
                          )
                        )
      } yield new PostgresDatasource(config, dataSource, quillContext)
    }
}

private final class PostgresDatasource(
  config: DatabaseAdapterConfig.Postgres,
  val dataSource: javax.sql.DataSource,
  val quillContext: Quill.Postgres[SnakeCase]
) extends QuillDatasource {

  override def healthCheck(): Task[HealthStatus] = {
    import quillContext.*
    inline def healthCheckQuery = quote {
      infix"SELECT 1 AS result".as[Query[HealthCheckResult]]
    }
    quillContext.run(healthCheckQuery)
  }.map { _ =>
    HealthStatus.Healthy(
      serviceName = "PostgreSQL",
      checkedAt = Instant.now(),
      details = Map(
        "host"     -> config.host,
        "port"     -> config.port.toString,
        "database" -> config.database
      )
    )
  }.catchAll { error =>
    ZIO.succeed(
      HealthStatus.Unhealthy(
        serviceName = "PostgreSQL",
        checkedAt = Instant.now(),
        error = error.getMessage,
        details = Map(
          "host"     -> config.host,
          "port"     -> config.port.toString,
          "database" -> config.database
        )
      )
    )
  }
}
