package com.cyrelis.srag.infrastructure.adapters.driven.database.postgres

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.ports.DatasourcePort
import com.cyrelis.srag.domain.ingestionjob.{IngestionJob, IngestionJobRepository}
import com.cyrelis.srag.infrastructure.adapters.driven.database.postgres.models.IngestionJobRow
import com.cyrelis.srag.infrastructure.resilience.ErrorMapper
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.*

object PostgresJobRepository {
  val layer: ZLayer[DatasourcePort, Nothing, IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]]] =
    ZLayer {
      for {
        datasource  <- ZIO.service[DatasourcePort]
        quillContext = datasource.asInstanceOf[PostgresDatasource].quillContext
      } yield new PostgresJobRepository(quillContext)
    }
}

private final class PostgresJobRepository(
  ctx: Quill.Postgres[SnakeCase]
) extends IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]] {

  import ctx.*

  private inline def jobs = quote(querySchema[IngestionJobRow]("ingestion_jobs"))

  override def create(job: IngestionJob): ZIO[Any, PipelineError, IngestionJob] =
    ErrorMapper.mapDatabaseError {
      val row = IngestionJobRow.fromDomain(job)

      inline def insert = quote {
        jobs.insertValue(lift(row)).onConflictIgnore.returning(j => j)
      }

      ctx.run(insert).map(IngestionJobRow.toDomain)
    }

  override def update(job: IngestionJob): ZIO[Any, PipelineError, IngestionJob] =
    ErrorMapper.mapDatabaseError {
      val row = IngestionJobRow.fromDomain(job)

      inline def updateQuery = quote {
        jobs
          .filter(_.id == lift(row.id))
          .updateValue(lift(row))
          .returning(j => j)
      }

      ctx.run(updateQuery).map(IngestionJobRow.toDomain)
    }

  override def findById(jobId: UUID): ZIO[Any, PipelineError, Option[IngestionJob]] =
    ErrorMapper.mapDatabaseError {
      inline def findQuery = quote {
        jobs.filter(_.id == lift(jobId))
      }

      ctx.run(findQuery).map(_.headOption.map(IngestionJobRow.toDomain))
    }

  override def listRunnable(now: Instant, limit: Int): ZIO[Any, PipelineError, List[IngestionJob]] =
    ErrorMapper.mapDatabaseError {
      inline def runnableQuery = quote {
        jobs
          .filter(job =>
            job.status != "Ready" && job.status != "DeadLetter" &&
              (job.status != "Failed" || job.attempt < job.maxAttempts)
          )
          .sortBy(_.createdAt)(using Ord.asc)
          .take(lift(limit))
      }

      ctx.run(runnableQuery).map(_.map(IngestionJobRow.toDomain))
    }

  override def listAll(): ZIO[Any, PipelineError, List[IngestionJob]] =
    ErrorMapper.mapDatabaseError {
      inline def allQuery = quote {
        jobs.sortBy(_.createdAt)(using Ord.desc)
      }

      ctx.run(allQuery).map(_.map(IngestionJobRow.toDomain))
    }
}
