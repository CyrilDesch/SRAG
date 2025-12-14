package com.cyrelis.srag.infrastructure.adapters.driving.gateway.mcp.handlers

import com.cyrelis.srag.infrastructure.adapters.driving.gateway.mcp.MCPModels.*
import io.circe.syntax.*
import zio.*

object InitializeHandler {

  private val SERVER_NAME      = "SRAG MCP Server"
  private val SERVER_VERSION   = "0.1.0"
  private val PROTOCOL_VERSION = "2024-11-05"

  def handleInitialize(
    request: JsonRpcRequest
  ): ZIO[Any, Throwable, Either[JsonRpcErrorResponse, JsonRpcSuccessResponse]] =
    for {
      _     <- ZIO.logInfo("Handling initialize")
      result = InitializeResult(
                protocolVersion = PROTOCOL_VERSION,
                capabilities = ServerCapabilities(
                  tools = Some(ToolsCapability(listChanged = Some(false)))
                ),
                serverInfo = ServerInfo(
                  name = SERVER_NAME,
                  version = SERVER_VERSION
                )
              )
    } yield Right(
      JsonRpcSuccessResponse(
        id = request.id,
        result = result.asJson
      )
    )

  def handleInitialized(
    request: JsonRpcRequest
  ): ZIO[Any, Throwable, Either[JsonRpcErrorResponse, JsonRpcSuccessResponse]] =
    ZIO.logInfo("Client initialized") *>
      ZIO.succeed(Right(JsonRpcSuccessResponse(id = request.id, result = io.circe.Json.obj())))

  def handlePing(request: JsonRpcRequest): ZIO[Any, Throwable, Either[JsonRpcErrorResponse, JsonRpcSuccessResponse]] =
    ZIO.succeed(Right(JsonRpcSuccessResponse(id = request.id, result = io.circe.Json.obj())))
}
