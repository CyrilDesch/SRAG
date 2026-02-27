package com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest

import java.nio.charset.StandardCharsets

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.ports.{
  BlobStorePort,
  EmbedderPort,
  LexicalStorePort,
  RerankerPort,
  TranscriberPort,
  VectorStorePort
}
import com.cyrelis.srag.application.usecases.healthcheck.HealthCheckService
import com.cyrelis.srag.application.usecases.ingestion.IngestService
import com.cyrelis.srag.application.usecases.query.QueryService
import com.cyrelis.srag.domain.ingestionjob.IngestionJobRepository
import com.cyrelis.srag.domain.transcript.TranscriptRepository
import com.cyrelis.srag.infrastructure.adapters.driving.Gateway
import com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.endpoint.{
  MainEndpoints,
  TestEndpoints,
  TestUiEndpoints
}
import com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.handler.{
  MainHandlers,
  TestHandlers,
  TestUiHandlers
}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.SwaggerUIOptions
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*
import zio.http.*

final class IngestRestGateway(
  host: String,
  port: Int,
  maxBodySizeBytes: Long
) extends Gateway {

  type TestEnv = TranscriberPort & EmbedderPort & BlobStorePort &
    TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]] & VectorStorePort & LexicalStorePort & RerankerPort &
    IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]]

  private def toHttpRoutes(endpoints: List[ZServerEndpoint[Any, ZioStreams]]): Routes[Any, Response] =
    ZioHttpInterpreter().toHttp(endpoints)

  private def buildRoutes
    : ZIO[IngestService & HealthCheckService & QueryService & TestEnv, Nothing, Routes[Any, Response]] =
    for {
      mainRoutes   <- buildMainRoutes
      testRoutes   <- buildTestRoutes
      testUiRoutes <- buildTestUiRoutes
      docsRoutes   <- buildDocsRoutes
      staticRoutes <- buildStaticRoutes
    } yield docsRoutes ++ mainRoutes ++ testRoutes ++ testUiRoutes ++ staticRoutes

  private def buildMainRoutes: ZIO[
    IngestService & HealthCheckService & QueryService & BlobStorePort &
      TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]],
    Nothing,
    Routes[Any, Response]
  ] =
    for {
      ingestPort      <- ZIO.service[IngestService]
      healthCheckPort <- ZIO.service[HealthCheckService]
      queryPort       <- ZIO.service[QueryService]
      blobStore       <- ZIO.service[BlobStorePort]
      transcriptRepo  <- ZIO.service[TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]]]
    } yield toHttpRoutes(
      List(
        MainEndpoints.health.zServerLogic(_ => MainHandlers.handleHealth.provide(ZLayer.succeed(healthCheckPort))),
        MainEndpoints.ingestAudioMultipart.zServerLogic(
          MainHandlers.handleIngestAudio(_).provide(ZLayer.succeed(ingestPort))
        ),
        MainEndpoints.ingestText.zServerLogic(MainHandlers.handleIngestText(_).provide(ZLayer.succeed(ingestPort))),
        MainEndpoints.jobStatus.zServerLogic(
          MainHandlers.handleGetJobStatus(_).provide(ZLayer.succeed(ingestPort))
        ),
        MainEndpoints.transcripts.zServerLogic { case (q, queryParams) =>
          val qpMap                                  = queryParams.toMap
          val filtersFromParams: Map[String, String] = qpMap.collect {
            case (k, v) if k.startsWith("metadata.") =>
              k.stripPrefix("metadata.") -> v
          }
          val filtersFromList: Map[String, String] = q.metadata.flatMap { entry =>
            val idx = entry.indexOf('=')
            if (idx > 0) Some(entry.substring(0, idx) -> entry.substring(idx + 1)) else None
          }.toMap
          val filters      = filtersFromParams ++ filtersFromList
          val sortBy       = q.sortBy
          val metadataSort = q.metadataSort
          val order        = q.order
          MainHandlers
            .handleGetTranscripts(filters, sortBy, metadataSort, order)
            .provide(ZLayer.succeed(transcriptRepo))
        },
        MainEndpoints.getFile.zServerLogic(
          MainHandlers.handleGetFile(_).provide(ZLayer.succeed(blobStore))
        ),
        MainEndpoints.query.zServerLogic(
          MainHandlers.handleQuery(_).provide(ZLayer.succeed(queryPort))
        )
      )
    )

  private def buildTestRoutes: ZIO[TestEnv, Nothing, Routes[Any, Response]] =
    for {
      transcriber  <- ZIO.service[TranscriberPort]
      embedder     <- ZIO.service[EmbedderPort]
      blobStore    <- ZIO.service[BlobStorePort]
      dbSink       <- ZIO.service[TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]]]
      vectorSink   <- ZIO.service[VectorStorePort]
      lexicalStore <- ZIO.service[LexicalStorePort]
      reranker     <- ZIO.service[RerankerPort]
    } yield toHttpRoutes(
      List(
        TestEndpoints.testTranscriber.zServerLogic(
          TestHandlers.handleTranscriber(_).provide(ZLayer.succeed(transcriber))
        ),
        TestEndpoints.testEmbedder.zServerLogic(TestHandlers.handleEmbedder(_).provide(ZLayer.succeed(embedder))),
        TestEndpoints.testBlobStore.zServerLogic(TestHandlers.handleBlobStore(_).provide(ZLayer.succeed(blobStore))),
        TestEndpoints.testDatabase.zServerLogic(TestHandlers.handleDatabase(_).provide(ZLayer.succeed(dbSink))),
        TestEndpoints.testVectorStore.zServerLogic(
          TestHandlers.handleVectorStore(_).provide(ZLayer.succeed(vectorSink) ++ ZLayer.succeed(embedder))
        ),
        TestEndpoints.testVectorStoreQuery.zServerLogic(
          TestHandlers.handleVectorStoreQuery(_).provide(ZLayer.succeed(vectorSink) ++ ZLayer.succeed(embedder))
        ),
        TestEndpoints.getAllTranscripts.zServerLogic(_ =>
          TestHandlers.handleGetAllTranscripts.provide(ZLayer.succeed(dbSink))
        ),
        TestEndpoints.testReranker.zServerLogic(
          TestHandlers.handleReranker(_).provide(ZLayer.succeed(reranker))
        ),
        TestEndpoints.testLexicalStoreIndex.zServerLogic(
          TestHandlers.handleLexicalStoreIndex(_).provide(ZLayer.succeed(lexicalStore))
        ),
        TestEndpoints.testLexicalStoreSearch.zServerLogic(
          TestHandlers.handleLexicalStoreSearch(_).provide(ZLayer.succeed(lexicalStore))
        )
      )
    )

  private def buildTestUiRoutes: ZIO[TestEnv, Nothing, Routes[Any, Response]] =
    for {
      jobRepo      <- ZIO.service[IngestionJobRepository[[X] =>> ZIO[Any, PipelineError, X]]]
      vectorStore  <- ZIO.service[VectorStorePort]
      blobStore    <- ZIO.service[BlobStorePort]
      lexicalStore <- ZIO.service[LexicalStorePort]
    } yield toHttpRoutes(
      List(
        TestUiEndpoints.listAllJobs.zServerLogic(_ =>
          TestUiHandlers.handleListAllJobs.provide(ZLayer.succeed(jobRepo))
        ),
        TestUiEndpoints.listAllVectors.zServerLogic(_ =>
          TestUiHandlers.handleListAllVectors.provide(ZLayer.succeed(vectorStore))
        ),
        TestUiEndpoints.listAllBlobs.zServerLogic(_ =>
          TestUiHandlers.handleListAllBlobs.provide(ZLayer.succeed(blobStore))
        ),
        TestUiEndpoints.listAllOpenSearch.zServerLogic(_ =>
          TestUiHandlers.handleListAllOpenSearch.provide(ZLayer.succeed(lexicalStore))
        )
      )
    )

  private def buildDocsRoutes: ZIO[Any, Nothing, Routes[Any, Response]] =
    ZIO.succeed {
      val docsEndpoints = SwaggerInterpreter(
        swaggerUIOptions = SwaggerUIOptions.default.pathPrefix(List("docs"))
      ).fromEndpoints[Task](
        MainEndpoints.all ++ TestEndpoints.all ++ TestUiEndpoints.all,
        "SRAG API",
        "v1"
      )
      ZioHttpInterpreter().toHttp(docsEndpoints)
    }

  private def buildStaticRoutes: ZIO[Any, Nothing, Routes[Any, Response]] =
    ZIO.succeed {
      val htmlHandler = Handler.fromZIO {
        ZIO.attemptBlocking {
          val stream = getClass.getClassLoader.getResourceAsStream("static/index.html")
          if (stream == null) throw new RuntimeException("static/index.html not found")
          try new String(stream.readAllBytes(), StandardCharsets.UTF_8)
          finally stream.close()
        }.map { html =>
          Response(
            status = Status.Ok,
            headers = Headers(Header.ContentType(MediaType.text.html)),
            body = Body.fromString(html)
          )
        }
          .catchAll(_ => ZIO.succeed(Response.text("Admin UI not available").status(Status.NotFound)))
      }

      Routes(
        Method.GET / ""   -> htmlHandler,
        Method.GET / "ui" -> htmlHandler
      )
    }

  def startWithDeps: ZIO[Scope & IngestService & HealthCheckService & QueryService & TestEnv, Throwable, Unit] =
    for {
      routes <- buildRoutes
      _      <- ZIO.logInfo(s"REST server will listen on $host:$port")
      _      <- {
        val docsUrl  = s"http://$host:$port/docs"
        val docsLink = s"\u001B]8;;$docsUrl\u0007$docsUrl\u001B]8;;\u0007"
        ZIO.logInfo(s"REST server docs will be available at $docsLink")
      }
      _ <- {
        val uiUrl  = s"http://$host:$port/ui"
        val uiLink = s"\u001B]8;;$uiUrl\u0007$uiUrl\u001B]8;;\u0007"
        ZIO.logInfo(s"Admin UI will be available at $uiLink")
      }
      _ <- Server
             .serve(routes)
             .provide(
               Server.defaultWith(
                 _.port(port).disableRequestStreaming(maxBodySizeBytes.toInt)
               )
             )
             .forkScoped
    } yield ()

  override def start: ZIO[Any, Throwable, Unit] =
    ZIO.logWarning("Gateway.start() called without dependencies - this should not happen in normal operation")

  override def description: String =
    s"REST API Gateway on $host:$port"
}
