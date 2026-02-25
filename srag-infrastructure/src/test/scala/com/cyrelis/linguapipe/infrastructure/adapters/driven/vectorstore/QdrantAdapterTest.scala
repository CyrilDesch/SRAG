package com.cyrelis.srag.infrastructure.adapters.driven.vectorstore

import com.cyrelis.srag.application.types.HealthStatus
import com.cyrelis.srag.infrastructure.config.VectorStoreAdapterConfig
import zio.*
import zio.test.*

object QdrantAdapterTest extends ZIOSpecDefault:

  private val invalidConfig: VectorStoreAdapterConfig.Qdrant = VectorStoreAdapterConfig.Qdrant(
    url = "http://nonexistent-host:6333",
    apiKey = Some(""),
    collection = "test-collection"
  )

  def spec = suite("QdrantAdapter health checks")(
    test("should return Unhealthy for unreachable Qdrant server") {
      val adapter = new QdrantAdapter(invalidConfig)

      for {
        status <- adapter.healthCheck()
      } yield assertTrue(
        status match {
          case HealthStatus.Unhealthy(serviceName, _, error, details) =>
            serviceName == s"Qdrant(${invalidConfig.collection})" &&
            error.nonEmpty &&
            details.contains("url") &&
            details("url") == invalidConfig.url &&
            details.contains("collection") &&
            details("collection") == invalidConfig.collection
          case _ => false
        }
      )
    },
    test("should include correct service name in health status") {
      val adapter = new QdrantAdapter(invalidConfig)

      for {
        status <- adapter.healthCheck()
      } yield {
        val serviceName = status match {
          case HealthStatus.Healthy(name, _, _)      => name
          case HealthStatus.Unhealthy(name, _, _, _) => name
          case HealthStatus.Timeout(name, _, _)      => name
        }
        assertTrue(serviceName == s"Qdrant(${invalidConfig.collection})")
      }
    },
    test("should include Qdrant connection details in unhealthy status") {
      val adapter = new QdrantAdapter(invalidConfig)

      for {
        status <- adapter.healthCheck()
      } yield {
        status match {
          case HealthStatus.Unhealthy(_, _, _, details) =>
            assertTrue(
              details.contains("url") &&
                details.contains("collection") &&
                details("url") == invalidConfig.url &&
                details("collection") == invalidConfig.collection
            )
          case _ => assertTrue(false)
        }
      }
    }
  )
