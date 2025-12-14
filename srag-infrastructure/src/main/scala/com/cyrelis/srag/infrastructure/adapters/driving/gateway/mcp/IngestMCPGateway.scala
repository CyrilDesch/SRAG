package com.cyrelis.srag.infrastructure.adapters.driving.gateway.mcp

import com.cyrelis.srag.application.ports.driving.{IngestPort, QueryPort}
import com.cyrelis.srag.infrastructure.adapters.driving.Gateway
import zio.*
import zio.stream.*
import scala.io.StdIn

final class IngestMCPGateway(ingestPort: IngestPort, queryPort: QueryPort) extends Gateway {
  override def description: String = "MCP Ingest Gateway (stdio)"

  override def start: ZIO[Any, Throwable, Unit] =
    ZIO.logInfo("Starting MCP Gateway on stdio...") *> {
      val handler = new IngestMCPRequestHandler(ingestPort, queryPort)

      val stdinStream = ZStream
        .repeatZIO(ZIO.attempt(StdIn.readLine()))
        .takeWhile(_ != null)
        .tap(line => ZIO.logDebug(s"Received: $line"))
        .mapZIO { line =>
          handler.handleRequest(line).flatMap { response =>
            if (response.isEmpty) {
              ZIO.logDebug("Notification handled - no response sent")
            } else {
              Console.printLine(response) *>
                ZIO.logDebug(s"Sent: $response")
            }
          }
        }
        .runDrain
        .catchAll { error =>
          ZIO.logError(s"MCP Gateway error: ${error.getMessage}") *>
            ZIO.fail(error)
        }

      ZIO.logInfo("MCP Gateway started, listening on stdin...") *>
        stdinStream
    }
}

object IngestMCPGateway {
  val layer: ZLayer[IngestPort & QueryPort, Nothing, IngestMCPGateway] =
    ZLayer.fromFunction((ingestPort: IngestPort, queryPort: QueryPort) => new IngestMCPGateway(ingestPort, queryPort))
}
