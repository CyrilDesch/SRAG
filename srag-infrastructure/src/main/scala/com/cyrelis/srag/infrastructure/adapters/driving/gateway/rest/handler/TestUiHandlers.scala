package com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.handler

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.ports.{BlobStorePort, LexicalStorePort, VectorStorePort}
import com.cyrelis.srag.domain.ingestionjob.IngestionJobRepository
import com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.testui.*
import com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.error.ErrorHandler
import zio.*

object TestUiHandlers {

  private final case class VectorListItem(
    id: String,
    transcriptId: Option[String],
    segmentIndex: Option[Int],
    vector: Option[List[Float]],
    payload: Option[Map[String, String]]
  )

  private final case class BlobListItem(
    key: String,
    filename: Option[String],
    contentType: Option[String],
    size: Option[Long],
    created: Option[String]
  )

  private final case class OpenSearchDocumentItem(
    id: String,
    transcriptId: Option[String],
    segmentIndex: Option[Int],
    text: Option[String],
    metadata: Option[Map[String, String]]
  )

  def handleListAllJobs: ZIO[IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]], String, List[AdminJobDto]] =
    ZIO
      .serviceWithZIO[IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]]](_.listAll())
      .map { jobs =>
        jobs.map { job =>
          AdminJobDto(
            jobId = job.id.toString,
            transcriptId = job.transcriptId.map(_.toString),
            status = job.status.toString,
            attempt = job.attempt,
            maxAttempts = job.maxAttempts,
            errorMessage = job.errorMessage,
            source = Some(job.source.toString),
            createdAt = job.createdAt.toString,
            updatedAt = job.updatedAt.toString,
            metadata = Some(job.metadata)
          )
        }
      }
      .mapError(ErrorHandler.errorToString)

  def handleListAllVectors: ZIO[VectorStorePort, String, AdminVectorsResponse] =
    ZIO
      .serviceWithZIO[VectorStorePort](_.listAllVectors())
      .map { vectors =>
        AdminVectorsResponse(
          total = vectors.size,
          vectors = vectors.map { vector =>
            AdminVectorDto(
              id = vector.id,
              transcriptId = vector.transcriptId.map(_.toString),
              segmentIndex = vector.segmentIndex,
              vector = vector.vector,
              payload = vector.payload
            )
          }
        )
      }
      .mapError(ErrorHandler.errorToString)

  def handleListAllBlobs: ZIO[BlobStorePort, String, List[AdminBlobDto]] =
    ZIO
      .serviceWithZIO[BlobStorePort](_.listAllBlobs())
      .map { blobs =>
        blobs.map { blob =>
          AdminBlobDto(
            key = blob.key,
            filename = blob.filename,
            contentType = blob.contentType,
            size = blob.size,
            created = blob.created.map(_.toString)
          )
        }
      }
      .mapError(ErrorHandler.errorToString)

  def handleListAllOpenSearch: ZIO[LexicalStorePort, String, AdminOpenSearchResponse] =
    ZIO
      .serviceWithZIO[LexicalStorePort](_.listAllDocuments())
      .map { documents =>
        AdminOpenSearchResponse(
          total = documents.size,
          documents = documents.map { doc =>
            AdminOpenSearchDocument(
              id = doc.id,
              transcriptId = doc.transcriptId.map(_.toString),
              segmentIndex = doc.segmentIndex,
              text = doc.text,
              metadata = doc.metadata
            )
          }
        )
      }
      .mapError(ErrorHandler.errorToString)
}
