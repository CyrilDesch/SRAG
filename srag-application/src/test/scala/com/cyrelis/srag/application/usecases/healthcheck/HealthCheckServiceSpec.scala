package com.cyrelis.srag.application.usecases.healthcheck

import com.cyrelis.srag.application.testsupport.ApplicationSharedSpec
import zio.*
import zio.test.*

object HealthCheckServiceSpec extends ApplicationSharedSpec {

  override def spec =
    suite("HealthCheckService")(
      test("checkAllServices returns all service statuses") {
        val expected = List(
          healthStatus("transcriber"),
          healthStatus("embedder"),
          healthStatus("datasource"),
          healthStatus("vector-store"),
          healthStatus("lexical-store"),
          healthStatus("reranker"),
          healthStatus("blob-store"),
          healthStatus("job-queue")
        )

        for {
          result <- (for {
                      service <- ZIO.service[HealthCheckService]
                      value   <- service.checkAllServices()
                    } yield value).provideLayer(
                      healthLayer(
                        makeHealthTranscriber(ZIO.succeed(expected(0))),
                        makeHealthEmbedder(ZIO.succeed(expected(1))),
                        makeHealthDatasource(ZIO.succeed(expected(2))),
                        makeHealthVectorStore(ZIO.succeed(expected(3))),
                        makeHealthLexicalStore(ZIO.succeed(expected(4))),
                        makeHealthReranker(ZIO.succeed(expected(5))),
                        makeHealthBlobStore(ZIO.succeed(expected(6))),
                        makeHealthQueue(ZIO.succeed(expected(7)))
                      )
                    )
        } yield assertTrue(result == expected)
      },
      test("checkAllServices fails if one health check crashes") {
        val boom = new RuntimeException("datasource down")
        for {
          result <- (for {
                      service <- ZIO.service[HealthCheckService]
                      value   <- service.checkAllServices().either
                    } yield value).provideLayer(
                      healthLayer(
                        makeHealthTranscriber(ZIO.succeed(healthStatus("transcriber"))),
                        makeHealthEmbedder(ZIO.succeed(healthStatus("embedder"))),
                        makeHealthDatasource(ZIO.fail(boom)),
                        makeHealthVectorStore(ZIO.succeed(healthStatus("vector-store"))),
                        makeHealthLexicalStore(ZIO.succeed(healthStatus("lexical-store"))),
                        makeHealthReranker(ZIO.succeed(healthStatus("reranker"))),
                        makeHealthBlobStore(ZIO.succeed(healthStatus("blob-store"))),
                        makeHealthQueue(ZIO.succeed(healthStatus("job-queue")))
                      )
                    )
        } yield assertTrue(
          result match {
            case Left(error) => error.getMessage == "datasource down"
            case _           => false
          }
        )
      }
    ) @@ TestAspect.withLiveClock
}
