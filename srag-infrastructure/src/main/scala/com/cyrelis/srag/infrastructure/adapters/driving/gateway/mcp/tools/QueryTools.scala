package com.cyrelis.srag.infrastructure.adapters.driving.gateway.mcp.tools

import com.cyrelis.srag.application.ports.driving.QueryPort
import com.cyrelis.srag.application.types.VectorStoreFilter
import com.cyrelis.srag.infrastructure.adapters.driving.gateway.mcp.MCPModels.*
import io.circe.Json
import zio.*

object QueryTools {

  def callQuery(queryPort: QueryPort, args: Option[Json]): ZIO[Any, Throwable, CallToolResult] =
    for {
      input <- ZIO.fromEither(
                 args
                   .toRight(new Exception("Missing arguments"))
                   .flatMap(_.as[QueryInput])
               )
      _        <- ZIO.logInfo(s"Querying: ${input.query}")
      filter    = input.metadata.map(VectorStoreFilter.apply)
      limit     = input.limit.getOrElse(5)
      segments <- queryPort
                    .retrieveContext(input.query, filter, limit)
                    .mapError(e => new Exception(s"Query error: ${e.message}"))
      _         <- ZIO.logInfo(s"Found ${segments.length} results")
      resultText = if (segments.isEmpty) {
                     "No results found for your query."
                   } else {
                     val results = segments.zipWithIndex.map { case (seg, idx) =>
                       s"""
                          |Result ${idx + 1} (Score: ${seg.score}):
                          |Transcript ID: ${seg.transcriptId}
                          |Segment Index: ${seg.segmentIndex}
                          |Text: ${seg.text}
                          |---""".stripMargin
                     }.mkString("\n")
                     s"Found ${segments.length} relevant segments:\n$results"
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
