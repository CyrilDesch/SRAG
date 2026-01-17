package com.cyrelis.srag.infrastructure.adapters.driven.database.postgres

import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.domain.transcript.{Transcript, TranscriptRepository}
import com.cyrelis.srag.infrastructure.adapters.driven.database.postgres.models.TranscriptRow
import com.cyrelis.srag.infrastructure.resilience.ErrorMapper
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.*

final class PostgresTranscriptRepository(
  ctx: Quill.Postgres[SnakeCase]
) extends TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]]:

  import ctx.*

  private inline def transcripts = quote(querySchema[TranscriptRow]("transcripts"))

  override def persist(transcript: Transcript): ZIO[Any, PipelineError, Unit] =
    ErrorMapper.mapDatabaseError {
      val transcriptRow = TranscriptRow.fromTranscript(transcript)

      inline def insertTranscript = quote {
        transcripts
          .insertValue(lift(transcriptRow))
          .onConflictUpdate(_.id)(
            (existing, excluded) => existing.language   -> excluded.language,
            (existing, excluded) => existing.words      -> excluded.words,
            (existing, excluded) => existing.confidence -> excluded.confidence,
            (existing, excluded) => existing.source     -> excluded.source,
            (existing, excluded) => existing.metadata   -> excluded.metadata
          )
      }

      ctx.run(insertTranscript) *> ZIO.logDebug(
        s"Persisted transcript ${transcript.id} to PostgreSQL"
      )
    }

  override def getAll(): ZIO[Any, PipelineError, List[Transcript]] =
    ErrorMapper.mapDatabaseError {
      inline def getAllTranscriptsQuery = quote {
        transcripts.sortBy(_.createdAt)(using Ord.desc)
      }

      ctx.run(getAllTranscriptsQuery).map(rows => rows.map(TranscriptRow.toTranscript))
    }

  override def getById(id: UUID): ZIO[Any, PipelineError, Option[Transcript]] =
    ErrorMapper.mapDatabaseError {
      inline def getByIdQuery(i: UUID) = quote {
        transcripts.filter(_.id == lift(i)).take(1)
      }
      ctx.run(getByIdQuery(id)).map(_.headOption.map(TranscriptRow.toTranscript))
    }
