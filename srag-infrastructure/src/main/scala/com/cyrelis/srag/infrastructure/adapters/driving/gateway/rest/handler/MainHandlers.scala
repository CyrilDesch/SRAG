package com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.handler

import java.nio.file.Files
import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.errors.PipelineError.ConfigurationError
import com.cyrelis.srag.application.model.query.VectorStoreFilter
import com.cyrelis.srag.application.ports.BlobStorePort
import com.cyrelis.srag.application.usecases.healthcheck.HealthCheckService
import com.cyrelis.srag.application.usecases.ingestion.IngestService
import com.cyrelis.srag.application.usecases.query.QueryService
import com.cyrelis.srag.domain.transcript.TranscriptRepository
import com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.main.*
import com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.error.ErrorHandler
import zio.*
import zio.stream.ZStream

object MainHandlers {

  def handleHealth: ZIO[HealthCheckService, String, List[HealthStatusRestDto]] =
    ZIO
      .serviceWithZIO[HealthCheckService](_.checkAllServices())
      .map(_.map(HealthStatusRestDto.fromDomain))
      .mapError(_.getMessage)

  def handleIngestAudio(req: AudioIngestMultipartDto): ZIO[IngestService, String, JobAcceptedRestDto] =
    (for {
      mediaContentType <- ZIO
                            .fromOption(req.file.contentType.map(_.toString))
                            .orElseFail(ConfigurationError("Missing Content-Type header in multipart request"))
      fileName <- ZIO
                    .fromOption(req.file.fileName)
                    .orElseFail(ConfigurationError("Missing filename in multipart request"))
      audioBytes <- ZIO.attempt(Files.readAllBytes(req.file.body.toPath))
      job        <-
        ZIO.serviceWithZIO[IngestService](
          _.submitAudio(audioBytes, mediaContentType, fileName, req.metadata)
        )
    } yield JobAcceptedRestDto.fromDomain(job)).mapError(ErrorHandler.errorToString)

  def handleIngestText(req: TextIngestRestDto): ZIO[IngestService, String, JobAcceptedRestDto] =
    ZIO
      .serviceWithZIO[IngestService](_.submitText(req.content, Map.empty))
      .map(JobAcceptedRestDto.fromDomain)
      .mapError(ErrorHandler.errorToString)

  def handleGetJobStatus(jobId: String): ZIO[IngestService, String, JobStatusRestDto] =
    (for {
      parsedId <-
        ZIO
          .attempt(UUID.fromString(jobId))
          .mapError(_ => com.cyrelis.srag.application.errors.PipelineError.ConfigurationError("Invalid job id"))
      jobOpt <- ZIO.serviceWithZIO[IngestService](_.getJob(parsedId))
      job    <- ZIO
               .fromOption(jobOpt)
               .orElseFail(
                 com.cyrelis.srag.application.errors.PipelineError.ConfigurationError(s"Job $jobId not found")
               )
    } yield JobStatusRestDto.fromDomain(job)).mapError(ErrorHandler.errorToString)

  def handleGetTranscripts(
    metadataFilters: Map[String, String],
    sortBy: Option[String],
    metadataSortKey: Option[String],
    order: Option[String]
  ): ZIO[TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]], String, List[TranscriptRestDto]] =
    ZIO
      .serviceWithZIO[TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]]](_.getAll())
      .map { transcripts =>
        val filtered = transcripts.filter { t =>
          val meta = t.metadata
          metadataFilters.forall { case (k, v) => meta.get(k).contains(v) }
        }

        val ascending = order.forall(_.equalsIgnoreCase("asc"))

        val sorted = (sortBy.map(_.toLowerCase) match {
          case Some("metadata") =>
            val key = metadataSortKey.getOrElse("")
            if (key.isEmpty) filtered.sortBy(_.createdAt.toString)
            else filtered.sortBy(t => t.metadata.getOrElse(key, ""))
          case _ =>
            filtered.sortBy(_.createdAt)
        })

        val maybeReversed = if (ascending) sorted else sorted.reverse
        maybeReversed.map(TranscriptRestDto.fromDomain)
      }
      .mapError(ErrorHandler.errorToString)

  def handleGetFile(
    blobKey: UUID
  ): ZIO[BlobStorePort, String, (String, String, ZStream[Any, Throwable, Byte])] =
    (for {
      blobStore   <- ZIO.service[BlobStorePort]
      blobKeyStr   = blobKey.toString
      filenameOpt <- blobStore.getBlobFilename(blobKeyStr)
      filename    <- ZIO
                    .fromOption(filenameOpt)
                    .orElseFail(
                      PipelineError.BlobStoreError(
                        s"Filename not found for blob $blobKey"
                      )
                    )
      contentTypeOpt <- blobStore.getBlobContentType(blobKeyStr)
      contentType    <- ZIO
                       .fromOption(contentTypeOpt)
                       .orElseFail(
                         PipelineError.BlobStoreError(
                           s"Content-Type not found for blob $blobKey"
                         )
                       )
    } yield {
      val stream =
        blobStore.fetchBlobAsStream(blobKeyStr).mapError(e => new RuntimeException(ErrorHandler.errorToString(e)))
      val contentDisposition = s"""attachment; filename="${filename.replace("\"", "\\\"")}""""
      (contentDisposition, contentType, stream)
    }).mapError(ErrorHandler.errorToString)

  def handleQuery(req: QueryRequestDto): ZIO[QueryService, String, List[QueryResponseDto]] =
    (for {
      queryPort <- ZIO.service[QueryService]
      filter     = req.metadata.map(VectorStoreFilter.apply)
      limit      = req.limit.getOrElse(5)
      segments  <-
        queryPort.retrieveContext(req.query, filter, limit)
    } yield segments.map(QueryResponseDto.fromDomain)).mapError(ErrorHandler.errorToString)

}
