package com.cyrelis.srag.infrastructure.adapters.driving.gateway.mcp

import io.circe.*
import io.circe.syntax.*
import io.circe.generic.semiauto.*

object MCPModels {

  case class JsonRpcRequest(
    jsonrpc: String = "2.0",
    id: Option[Json],
    method: String,
    params: Option[Json] = None
  )

  case class JsonRpcSuccessResponse(
    jsonrpc: String = "2.0",
    id: Option[Json],
    result: Json
  )

  case class JsonRpcErrorResponse(
    jsonrpc: String = "2.0",
    id: Option[Json],
    error: JsonRpcError
  )

  case class JsonRpcError(
    code: Int,
    message: String,
    data: Option[Json] = None
  )

  case class InitializeParams(
    protocolVersion: String,
    capabilities: ClientCapabilities,
    clientInfo: ClientInfo
  )

  case class ClientCapabilities(
    experimental: Option[Json] = None,
    sampling: Option[Json] = None
  )

  case class ClientInfo(
    name: String,
    version: String
  )

  case class InitializeResult(
    protocolVersion: String,
    capabilities: ServerCapabilities,
    serverInfo: ServerInfo
  )

  case class ServerCapabilities(
    tools: Option[ToolsCapability] = Some(ToolsCapability())
  )

  case class ToolsCapability(
    listChanged: Option[Boolean] = Some(false)
  )

  case class ServerInfo(
    name: String,
    version: String
  )

  case class ListToolsResult(
    tools: List[Tool]
  )

  case class Tool(
    name: String,
    description: String,
    inputSchema: Json
  )

  case class CallToolParams(
    name: String,
    arguments: Option[Json]
  )

  case class CallToolResult(
    content: List[ToolContent],
    isError: Boolean = false
  )

  case class ToolContent(
    `type`: String,
    text: String
  )
  case class IngestAudioInput(
    audioBase64: String,
    mediaContentType: String,
    mediaFilename: String,
    metadata: Option[Map[String, String]]
  )

  case class IngestTextInput(
    text: String,
    metadata: Option[Map[String, String]]
  )

  case class IngestDocumentInput(
    documentContent: String,
    mediaType: String,
    metadata: Option[Map[String, String]]
  )

  case class QueryInput(
    query: String,
    metadata: Option[Map[String, String]],
    limit: Option[Int]
  )

  case class GetJobStatusInput(
    jobId: String
  )

  given Codec[JsonRpcRequest]         = deriveCodec
  given Codec[JsonRpcSuccessResponse] = deriveCodec
  given Codec[JsonRpcErrorResponse]   = deriveCodec
  given Codec[JsonRpcError]           = deriveCodec
  given Codec[InitializeParams]       = deriveCodec
  given Codec[ClientCapabilities]     = deriveCodec
  given Codec[ClientInfo]             = deriveCodec
  given Codec[InitializeResult]       = deriveCodec
  given Codec[ServerCapabilities]     = deriveCodec
  given Codec[ToolsCapability]        = deriveCodec
  given Codec[ServerInfo]             = deriveCodec
  given Codec[ListToolsResult]        = deriveCodec
  given Codec[Tool]                   = deriveCodec
  given Codec[CallToolParams]         = deriveCodec
  given Codec[ToolContent]            = deriveCodec

  given Encoder[CallToolResult] = Encoder.instance { result =>
    Json.obj(
      "content" -> result.content.asJson,
      "isError" -> result.isError.asJson
    )
  }

  given Decoder[CallToolResult] = Decoder.instance { c =>
    for {
      content <- c.downField("content").as[List[ToolContent]]
      isError <- c.downField("isError").as[Option[Boolean]].map(_.getOrElse(false))
    } yield CallToolResult(content, isError)
  }
  given Codec[IngestAudioInput]    = deriveCodec
  given Codec[IngestTextInput]     = deriveCodec
  given Codec[IngestDocumentInput] = deriveCodec
  given Codec[QueryInput]          = deriveCodec
  given Codec[GetJobStatusInput]   = deriveCodec
}
