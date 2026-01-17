package com.cyrelis.srag.infrastructure.adapters.driven.blobstore

import com.cyrelis.srag.application.types.HealthStatus
import com.cyrelis.srag.infrastructure.config.BlobStoreAdapterConfig
import zio.*
import zio.test.*

object MinioAdapterTest extends ZIOSpecDefault:

  private val invalidConfig: BlobStoreAdapterConfig.MinIO = BlobStoreAdapterConfig.MinIO(
    host = "nonexistent-host",
    port = 9999,
    accessKey = "invalid-key",
    secretKey = "invalid-secret",
    bucket = "test-bucket"
  )

  private val invalidEndpoint: String = s"${invalidConfig.host}:${invalidConfig.port}"

  def spec = suite("MinioAdapter health checks")(
    test("should return Unhealthy for unreachable MinIO server") {
      val adapter = new MinioAdapter(
        host = invalidConfig.host,
        port = invalidConfig.port,
        accessKey = invalidConfig.accessKey,
        secretKey = invalidConfig.secretKey,
        bucket = invalidConfig.bucket
      )

      for {
        status <- adapter.healthCheck()
      } yield assertTrue(
        status match {
          case HealthStatus.Unhealthy(serviceName, _, error, details) =>
            serviceName == s"MinIO($invalidEndpoint)" &&
            error.nonEmpty &&
            details.contains("endpoint") &&
            details("endpoint") == invalidEndpoint &&
            details.contains("bucket") &&
            details("bucket") == invalidConfig.bucket
          case _ => false
        }
      )
    },
    test("should include correct service name in health status") {
      val adapter = new MinioAdapter(
        host = invalidConfig.host,
        port = invalidConfig.port,
        accessKey = invalidConfig.accessKey,
        secretKey = invalidConfig.secretKey,
        bucket = invalidConfig.bucket
      )

      for {
        status <- adapter.healthCheck()
      } yield {
        val serviceName = status match {
          case HealthStatus.Healthy(name, _, _)      => name
          case HealthStatus.Unhealthy(name, _, _, _) => name
          case HealthStatus.Timeout(name, _, _)      => name
        }
        assertTrue(serviceName == s"MinIO($invalidEndpoint)")
      }
    },
    test("should include MinIO connection details in unhealthy status") {
      val adapter = new MinioAdapter(
        host = invalidConfig.host,
        port = invalidConfig.port,
        accessKey = invalidConfig.accessKey,
        secretKey = invalidConfig.secretKey,
        bucket = invalidConfig.bucket
      )

      for {
        status <- adapter.healthCheck()
      } yield {
        status match {
          case HealthStatus.Unhealthy(_, _, _, details) =>
            assertTrue(
              details.contains("endpoint") &&
                details.contains("bucket") &&
                details("endpoint") == invalidEndpoint &&
                details("bucket") == invalidConfig.bucket
            )
          case _ => assertTrue(false)
        }
      }
    }
  )
