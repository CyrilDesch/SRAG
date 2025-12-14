package com.cyrelis.srag.infrastructure.adapters.driving.gateway.mcp.tools

import java.util.Base64

import com.cyrelis.srag.application.ports.driving.IngestPort
import com.cyrelis.srag.infrastructure.adapters.driving.gateway.mcp.MCPModels.*
import io.circe.Json
import zio.*

object IngestTools {

  def callIngestText(ingestPort: IngestPort, args: Option[Json]): ZIO[Any, Throwable, CallToolResult] =
    for {
      input <- ZIO.fromEither(
                args
                  .toRight(new Exception("Missing arguments"))
                  .flatMap(_.as[IngestTextInput])
              )
      _   <- ZIO.logInfo(s"Ingesting text: ${input.text.take(100)}...")
      job <- ingestPort
              .submitText(input.text, input.metadata.getOrElse(Map.empty))
              .mapError(e => new Exception(s"Ingest error: ${e.message}"))
      _ <- ZIO.logInfo(s"Text ingestion job created: ${job.id}")
    } yield CallToolResult(
      content = List(
        ToolContent(
          `type` = "text",
          text = s"""Text ingestion job created successfully!
                    |Job ID: ${job.id}
                    |Status: ${job.status}
                    |Created at: ${job.createdAt}
                    |
                    |Use the 'get_job_status' tool to check the progress.""".stripMargin
        )
      )
    )

  def callIngestAudio(ingestPort: IngestPort, args: Option[Json]): ZIO[Any, Throwable, CallToolResult] =
    for {
      input <- ZIO.fromEither(
                args
                  .toRight(new Exception("Missing arguments"))
                  .flatMap(_.as[IngestAudioInput])
              )
      _          <- ZIO.logInfo(s"Ingesting audio: ${input.mediaFilename}")
      audioBytes <- ZIO.attempt(Base64.getDecoder.decode(input.audioBase64))
      job        <- ingestPort
              .submitAudio(
                audioBytes,
                input.mediaContentType,
                input.mediaFilename,
                input.metadata.getOrElse(Map.empty)
              )
              .mapError(e => new Exception(s"Ingest error: ${e.message}"))
      _ <- ZIO.logInfo(s"Audio ingestion job created: ${job.id}")
    } yield CallToolResult(
      content = List(
        ToolContent(
          `type` = "text",
          text = s"""Audio ingestion job created successfully!
                    |Job ID: ${job.id}
                    |Status: ${job.status}
                    |Filename: ${input.mediaFilename}
                    |Created at: ${job.createdAt}
                    |
                    |Use the 'get_job_status' tool to check the progress.""".stripMargin
        )
      )
    )

  def callIngestDocument(ingestPort: IngestPort, args: Option[Json]): ZIO[Any, Throwable, CallToolResult] =
    for {
      input <- ZIO.fromEither(
                args
                  .toRight(new Exception("Missing arguments"))
                  .flatMap(_.as[IngestDocumentInput])
              )
      _   <- ZIO.logInfo(s"Ingesting document with media type: ${input.mediaType}")
      job <- ingestPort
              .submitDocument(
                input.documentContent,
                input.mediaType,
                input.metadata.getOrElse(Map.empty)
              )
              .mapError(e => new Exception(s"Ingest error: ${e.message}"))
      _ <- ZIO.logInfo(s"Document ingestion job created: ${job.id}")
    } yield CallToolResult(
      content = List(
        ToolContent(
          `type` = "text",
          text = s"""Document ingestion job created successfully!
                    |Job ID: ${job.id}
                    |Status: ${job.status}
                    |Media type: ${input.mediaType}
                    |Created at: ${job.createdAt}
                    |
                    |Use the 'get_job_status' tool to check the progress.""".stripMargin
        )
      )
    )
}
