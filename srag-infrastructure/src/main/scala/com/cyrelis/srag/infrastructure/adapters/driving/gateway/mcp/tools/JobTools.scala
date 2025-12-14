package com.cyrelis.srag.infrastructure.adapters.driving.gateway.mcp.tools

import java.util.UUID

import com.cyrelis.srag.application.ports.driving.IngestPort
import com.cyrelis.srag.infrastructure.adapters.driving.gateway.mcp.MCPModels.*
import io.circe.Json
import zio.*

object JobTools {

  def callGetJobStatus(ingestPort: IngestPort, args: Option[Json]): ZIO[Any, Throwable, CallToolResult] =
    for {
      input <- ZIO.fromEither(
                args
                  .toRight(new Exception("Missing arguments"))
                  .flatMap(_.as[GetJobStatusInput])
              )
      jobId  <- ZIO.attempt(UUID.fromString(input.jobId))
      _      <- ZIO.logInfo(s"Getting job status: $jobId")
      jobOpt <- ingestPort
                  .getJob(jobId)
                  .mapError(e => new Exception(s"Get job error: ${e.message}"))
      resultText = jobOpt match {
                    case Some(job) =>
                      s"""Job Status:
                          |Job ID: ${job.id}
                          |Transcript ID: ${job.transcriptId.map(_.toString).getOrElse("N/A")}
                          |Status: ${job.status}
                          |Source: ${job.source}
                          |Attempt: ${job.attempt}/${job.maxAttempts}
                          |Created at: ${job.createdAt}
                          |Updated at: ${job.updatedAt}
                          |${job.errorMessage.map(e => s"Error: $e").getOrElse("")}
                          |Metadata: ${job.metadata.map { case (k, v) => s"$k=$v" }.mkString(", ")}""".stripMargin
                    case None =>
                      s"Job with ID ${input.jobId} not found."
                  }
    } yield CallToolResult(
      content = List(
        ToolContent(
          `type` = "text",
          text = resultText
        )
      )
    )
}
