package com.cyrelis.srag.infrastructure.adapters.driving.gateway.mcp.handlers

import com.cyrelis.srag.infrastructure.adapters.driving.gateway.mcp.MCPModels.*
import io.circe.*
import io.circe.syntax.*
import zio.*

object ToolsListHandler {

  def handleListTools(
    request: JsonRpcRequest
  ): ZIO[Any, Throwable, Either[JsonRpcErrorResponse, JsonRpcSuccessResponse]] =
    for {
      _    <- ZIO.logInfo("Listing tools")
      tools = List(
                Tool(
                  name = "ingest_text",
                  description = "Ingest text content into the SRAG system for indexing and retrieval",
                  inputSchema = Json.obj(
                    "type"       -> "object".asJson,
                    "properties" -> Json.obj(
                      "text" -> Json.obj(
                        "type"        -> "string".asJson,
                        "description" -> "The text content to ingest".asJson
                      ),
                      "metadata" -> Json.obj(
                        "type"                 -> "object".asJson,
                        "description"          -> "Optional metadata key-value pairs".asJson,
                        "additionalProperties" -> Json.obj("type" -> "string".asJson)
                      )
                    ),
                    "required" -> Json.arr("text".asJson)
                  )
                ),
                Tool(
                  name = "ingest_audio",
                  description = "Ingest audio file (base64 encoded) for transcription and indexing",
                  inputSchema = Json.obj(
                    "type"       -> "object".asJson,
                    "properties" -> Json.obj(
                      "audioBase64" -> Json.obj(
                        "type"        -> "string".asJson,
                        "description" -> "Base64 encoded audio content".asJson
                      ),
                      "mediaContentType" -> Json.obj(
                        "type"        -> "string".asJson,
                        "description" -> "MIME type of the audio (e.g., audio/wav, audio/mp3)".asJson
                      ),
                      "mediaFilename" -> Json.obj(
                        "type"        -> "string".asJson,
                        "description" -> "Original filename of the audio".asJson
                      ),
                      "metadata" -> Json.obj(
                        "type"                 -> "object".asJson,
                        "description"          -> "Optional metadata key-value pairs".asJson,
                        "additionalProperties" -> Json.obj("type" -> "string".asJson)
                      )
                    ),
                    "required" -> Json.arr("audioBase64".asJson, "mediaContentType".asJson, "mediaFilename".asJson)
                  )
                ),
                Tool(
                  name = "ingest_document",
                  description = "Ingest document content (PDF, text, etc.) for parsing and indexing",
                  inputSchema = Json.obj(
                    "type"       -> "object".asJson,
                    "properties" -> Json.obj(
                      "documentContent" -> Json.obj(
                        "type"        -> "string".asJson,
                        "description" -> "Base64 encoded document content".asJson
                      ),
                      "mediaType" -> Json.obj(
                        "type"        -> "string".asJson,
                        "description" -> "MIME type of the document (e.g., application/pdf)".asJson
                      ),
                      "metadata" -> Json.obj(
                        "type"                 -> "object".asJson,
                        "description"          -> "Optional metadata key-value pairs".asJson,
                        "additionalProperties" -> Json.obj("type" -> "string".asJson)
                      )
                    ),
                    "required" -> Json.arr("documentContent".asJson, "mediaType".asJson)
                  )
                ),
                Tool(
                  name = "query",
                  description = "Query the SRAG system to retrieve relevant context segments",
                  inputSchema = Json.obj(
                    "type"       -> "object".asJson,
                    "properties" -> Json.obj(
                      "query" -> Json.obj(
                        "type"        -> "string".asJson,
                        "description" -> "The search query text".asJson
                      ),
                      "metadata" -> Json.obj(
                        "type"                 -> "object".asJson,
                        "description"          -> "Optional metadata filters".asJson,
                        "additionalProperties" -> Json.obj("type" -> "string".asJson)
                      ),
                      "limit" -> Json.obj(
                        "type"        -> "integer".asJson,
                        "description" -> "Maximum number of results to return (default: 5)".asJson,
                        "default"     -> 5.asJson
                      )
                    ),
                    "required" -> Json.arr("query".asJson)
                  )
                ),
                Tool(
                  name = "get_job_status",
                  description = "Get the status of an ingestion job",
                  inputSchema = Json.obj(
                    "type"       -> "object".asJson,
                    "properties" -> Json.obj(
                      "jobId" -> Json.obj(
                        "type"        -> "string".asJson,
                        "description" -> "The UUID of the ingestion job".asJson
                      )
                    ),
                    "required" -> Json.arr("jobId".asJson)
                  )
                )
              )
      result = ListToolsResult(tools)
    } yield Right(
      JsonRpcSuccessResponse(
        id = request.id,
        result = result.asJson
      )
    )
}
