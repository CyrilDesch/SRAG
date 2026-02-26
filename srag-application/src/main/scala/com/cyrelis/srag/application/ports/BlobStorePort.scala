package com.cyrelis.srag.application.ports

import java.time.Instant
import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.model.healthcheck.HealthStatus
import zio.*
import zio.stream.ZStream

final case class BlobInfo(
  key: String,
  filename: Option[String],
  contentType: Option[String],
  size: Option[Long],
  created: Option[Instant]
)

trait BlobStorePort {
  def storeAudio(
    jobId: UUID,
    audioContent: Array[Byte],
    mediaContentType: String,
    mediaFilename: String
  ): ZIO[Any, PipelineError, String]
  def storeText(jobId: UUID, textContent: String): ZIO[Any, PipelineError, String]
  def fetchAudio(blobKey: String): ZIO[Any, PipelineError, Array[Byte]]
  def fetchBlobAsStream(blobKey: String): ZStream[Any, PipelineError, Byte]
  def getBlobFilename(blobKey: String): ZIO[Any, PipelineError, Option[String]]
  def getBlobContentType(blobKey: String): ZIO[Any, PipelineError, Option[String]]
  def deleteBlob(blobKey: String): ZIO[Any, PipelineError, Unit]
  def storeDocument(jobId: UUID, documentContent: String, mediaType: String): ZIO[Any, PipelineError, String]
  def listAllBlobs(): ZIO[Any, PipelineError, List[BlobInfo]]
  def healthCheck(): Task[HealthStatus]
}
