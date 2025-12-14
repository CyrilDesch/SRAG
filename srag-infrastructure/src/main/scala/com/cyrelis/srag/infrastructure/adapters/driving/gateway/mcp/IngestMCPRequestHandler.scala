package com.cyrelis.srag.infrastructure.adapters.driving.gateway.mcp

import com.cyrelis.srag.application.ports.driving.{IngestPort, QueryPort}
import com.cyrelis.srag.infrastructure.adapters.driving.gateway.mcp.MCPModels.*
import com.cyrelis.srag.infrastructure.adapters.driving.gateway.mcp.handlers.{InitializeHandler, ToolsListHandler}
import com.cyrelis.srag.infrastructure.adapters.driving.gateway.mcp.tools.{IngestTools, JobTools, QueryTools}
import io.circe.parser.*
import io.circe.syntax.*
import zio.*

final class IngestMCPRequestHandler(
  ingestPort: IngestPort,
  queryPort: QueryPort
) {

  def handleRequest(requestJson: String): ZIO[Any, Throwable, String] =
    ZIO.logInfo(s"Received request: $requestJson") *>
      (for {
        request <- ZIO.fromEither(decode[JsonRpcRequest](requestJson))
        _       <- ZIO.logInfo(s"Parsed method: ${request.method}")
        result <- if (request.id.isEmpty && request.method.startsWith("notifications/")) {
                    ZIO.logInfo(s"Ignoring notification: ${request.method}") *>
                      ZIO.succeed(None)
                  } else {
                    val response = request.method match {
                      case "initialize"                => InitializeHandler.handleInitialize(request)
                      case "initialized"               => InitializeHandler.handleInitialized(request)
                      case "notifications/initialized" => InitializeHandler.handleInitialized(request)
                      case "tools/list"                => ToolsListHandler.handleListTools(request)
                      case "tools/call"                => handleCallTool(request)
                      case "ping"                      => InitializeHandler.handlePing(request)
                      case other if request.id.isEmpty =>
                        ZIO.logInfo(s"Ignoring unknown notification: $other") *>
                          ZIO.succeed(Right(JsonRpcSuccessResponse(id = None, result = io.circe.Json.obj())))
                      case other =>
                        ZIO.logWarning(s"Unknown method: $other") *>
                          ZIO.succeed(Left(createErrorResponse(request.id, -32601, s"Method not found: $other")))
                    }
                    response.map(Some(_))
                  }
      } yield result).catchAll { error =>
        ZIO.logError(s"Error handling request: ${error.getMessage}") *>
          ZIO.succeed(
            Some(Left(createErrorResponse(None, -32603, s"Internal error: ${error.getMessage}")))
          )
      }.map {
        case Some(Right(success)) => success.asJson.noSpaces
        case Some(Left(error))    => error.asJson.noSpaces
        case None                 => ""
      }

  private def handleCallTool(
    request: JsonRpcRequest
  ): ZIO[Any, Throwable, Either[JsonRpcErrorResponse, JsonRpcSuccessResponse]] =
    (for {
      params <- ZIO.fromEither(
                  request.params
                    .toRight(new Exception("Missing params"))
                    .flatMap(_.as[CallToolParams])
                )
      _      <- ZIO.logInfo(s"Calling tool: ${params.name}")
      result <- params.name match {
                  case "ingest_text"     => IngestTools.callIngestText(ingestPort, params.arguments)
                  case "ingest_audio"    => IngestTools.callIngestAudio(ingestPort, params.arguments)
                  case "ingest_document" => IngestTools.callIngestDocument(ingestPort, params.arguments)
                  case "query"           => QueryTools.callQuery(queryPort, params.arguments)
                  case "get_job_status"  => JobTools.callGetJobStatus(ingestPort, params.arguments)
                  case other             =>
                    ZIO.fail(new Exception(s"Unknown tool: $other"))
                }
    } yield Right(
      JsonRpcSuccessResponse(
        id = request.id,
        result = result.asJson
      )
    ): Either[JsonRpcErrorResponse, JsonRpcSuccessResponse]).catchAll { error =>
      ZIO.logError(s"Tool call error: ${error.getMessage}") *>
        ZIO.succeed(Left(createErrorResponse(request.id, -32602, s"Tool execution failed: ${error.getMessage}")))
    }

  private def createErrorResponse(id: Option[io.circe.Json], code: Int, message: String): JsonRpcErrorResponse =
    JsonRpcErrorResponse(
      id = id,
      error = JsonRpcError(code, message)
    )
}
