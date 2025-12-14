package com.cyrelis.srag.infrastructure

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.ports.driven.datasource.DatasourcePort
import com.cyrelis.srag.application.ports.driven.embedding.EmbedderPort
import com.cyrelis.srag.application.ports.driven.parser.DocumentParserPort
import com.cyrelis.srag.application.ports.driven.reranker.RerankerPort
import com.cyrelis.srag.application.ports.driven.storage.{BlobStorePort, LexicalStorePort, VectorStorePort}
import com.cyrelis.srag.application.ports.driven.transcription.TranscriberPort
import com.cyrelis.srag.application.ports.driving.{IngestPort, QueryPort}
import com.cyrelis.srag.application.workers.DefaultIngestionJobWorker
import com.cyrelis.srag.domain.ingestionjob.IngestionJobRepository
import com.cyrelis.srag.domain.transcript.TranscriptRepository
import com.cyrelis.srag.infrastructure.adapters.driving.gateway.mcp.IngestMCPGateway
import com.cyrelis.srag.infrastructure.config.RuntimeConfig
import com.cyrelis.srag.infrastructure.runtime.ModuleWiring
import zio.*

/**
 * Dedicated MCP Server entry point. This main class starts only the MCP gateway
 * over stdin/stdout, without health checks, migrations, or other services. It's
 * designed to be packaged as a standalone JAR for integration with Claude.
 */
object MCPMain extends ZIOAppDefault {

  type MCPDependencies = RuntimeConfig & IngestPort & QueryPort & IngestMCPGateway & TranscriberPort & EmbedderPort &
    TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]] & VectorStorePort & LexicalStorePort & RerankerPort &
    BlobStorePort & DocumentParserPort & IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]] & DatasourcePort &
    DefaultIngestionJobWorker

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> zio.logging.backend.SLF4J.slf4j

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    mcpServerProgram.provide(
      RuntimeConfig.layer
        .tapError(err => ZIO.logError(s"Failed to load configuration: ${err.getMessage}"))
        .orDie,
      ModuleWiring.transcriberLayer,
      ModuleWiring.embedderLayer,
      ModuleWiring.datasourceLayer,
      ModuleWiring.transcriptRepositoryLayer,
      ModuleWiring.vectorSinkLayer,
      ModuleWiring.lexicalStoreLayer,
      ModuleWiring.rerankerLayer,
      ModuleWiring.blobStoreLayer,
      ModuleWiring.documentParserLayer,
      ModuleWiring.jobRepositoryLayer,
      ModuleWiring.jobQueueLayer,
      ModuleWiring.audioSourcePreparatorLayer,
      ModuleWiring.textSourcePreparatorLayer,
      ModuleWiring.commonIndexingPipelineLayer,
      ModuleWiring.ingestServiceLayer,
      ModuleWiring.jobWorkerLayer,
      ModuleWiring.queryServiceLayer,
      IngestMCPGateway.layer
    )

  private def mcpServerProgram: ZIO[MCPDependencies, Nothing, Unit] =
    ZIO.scoped {
      for {
        _       <- ZIO.logInfo("Starting MCP Server...")
        worker  <- ZIO.service[DefaultIngestionJobWorker]
        _       <- ZIO.logInfo("Starting ingestion job worker...")
        _       <- worker.run.forkScoped
        gateway <- ZIO.service[IngestMCPGateway]
        _       <- gateway.start
      } yield ()
    }.orDie
}
