package com.cyrelis.srag.application.ports

import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.model.healthcheck.HealthStatus
import com.cyrelis.srag.application.model.query.{LexicalSearchResult, VectorStoreFilter}
import zio.*

final case class DocumentInfo(
  id: String,
  transcriptId: Option[UUID],
  segmentIndex: Option[Int],
  text: Option[String],
  metadata: Option[Map[String, String]]
)

trait LexicalStorePort {
  def indexSegments(
    transcriptId: UUID,
    segments: List[(Int, String)],
    metadata: Map[String, String]
  ): ZIO[Any, PipelineError, Unit]

  def deleteTranscript(transcriptId: UUID): ZIO[Any, PipelineError, Unit]

  def search(
    queryText: String,
    limit: Int,
    filter: Option[VectorStoreFilter] = None
  ): ZIO[Any, PipelineError, List[LexicalSearchResult]]

  def listAllDocuments(): ZIO[Any, PipelineError, List[DocumentInfo]]

  def healthCheck(): Task[HealthStatus]
}
