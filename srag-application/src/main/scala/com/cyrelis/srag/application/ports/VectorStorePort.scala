package com.cyrelis.srag.application.ports

import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.model.healthcheck.HealthStatus
import com.cyrelis.srag.application.model.query.{VectorSearchResult, VectorStoreFilter}
import zio.*

final case class VectorInfo(
  id: String,
  transcriptId: Option[UUID],
  segmentIndex: Option[Int],
  vector: Option[List[Float]],
  payload: Option[Map[String, String]]
)

trait VectorStorePort {
  def upsertEmbeddings(
    transcriptId: UUID,
    vectors: List[Array[Float]],
    metadata: Map[String, String]
  ): ZIO[Any, PipelineError, Unit]
  def searchSimilar(
    queryVector: Array[Float],
    limit: Int,
    filter: Option[VectorStoreFilter] = None
  ): ZIO[Any, PipelineError, List[VectorSearchResult]]
  def listAllVectors(): ZIO[Any, PipelineError, List[VectorInfo]]
  def healthCheck(): Task[HealthStatus]
}
