package com.cyrelis.srag.infrastructure.adapters.driven.blobstore

import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.UUID

import scala.jdk.CollectionConverters.*

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.model.healthcheck.HealthStatus
import com.cyrelis.srag.application.ports.{BlobInfo, BlobStorePort}
import com.cyrelis.srag.infrastructure.config.BlobStoreAdapterConfig
import io.minio.*
import zio.*
import zio.stream.ZStream

private final class MinioAdapter(minioClient: MinioClient, endpoint: String, bucket: String) extends BlobStorePort {

  override def storeAudio(
    jobId: UUID,
    audioContent: Array[Byte],
    mediaContentType: String,
    mediaFilename: String
  ): ZIO[Any, PipelineError, String] = {
    for {
      blobKey <- ZIO.succeed(UUID.randomUUID().toString)
      metadata = Map(
                   "x-amz-meta-original-filename" -> mediaFilename,
                   "x-amz-meta-content-type"      -> mediaContentType
                 ).asJava
      _ <- uploadBlobWithMetadata(blobKey, audioContent, mediaContentType, metadata)
    } yield blobKey
  }
    .mapError(error => PipelineError.BlobStoreError(error.getMessage, Some(error)))

  override def storeText(jobId: UUID, textContent: String): ZIO[Any, PipelineError, String] = {
    for {
      blobKey <- ZIO.succeed(UUID.randomUUID().toString)
      filename = s"text-${java.lang.System.currentTimeMillis()}.txt"
      metadata = Map(
                   "x-amz-meta-original-filename" -> filename,
                   "x-amz-meta-content-type"      -> "text/plain"
                 ).asJava
      contentBytes <- ZIO.succeed(textContent.getBytes("UTF-8"))
      _            <- uploadBlobWithMetadata(blobKey, contentBytes, "text/plain", metadata)
    } yield blobKey
  }
    .mapError(error => PipelineError.BlobStoreError(error.getMessage, Some(error)))

  override def fetchAudio(blobKey: String): ZIO[Any, PipelineError, Array[Byte]] = {
    for {
      inputStream <-
        ZIO.attemptBlocking(minioClient.getObject(GetObjectArgs.builder().bucket(bucket).`object`(blobKey).build()))
      bytes <-
        ZIO.attemptBlocking(inputStream.readAllBytes()).ensuring(ZIO.attemptBlocking(inputStream.close()).ignore)
    } yield bytes
  }
    .mapError(error => PipelineError.BlobStoreError(error.getMessage, Some(error)))

  override def fetchBlobAsStream(blobKey: String): ZStream[Any, PipelineError, Byte] =
    ZStream
      .fromZIO(
        ZIO
          .attemptBlocking(minioClient.getObject(GetObjectArgs.builder().bucket(bucket).`object`(blobKey).build()))
          .mapError(error => PipelineError.BlobStoreError(error.getMessage, Some(error)))
      )
      .flatMap(inputStream =>
        ZStream
          .fromInputStream(inputStream)
          .mapError(ioe => PipelineError.BlobStoreError(ioe.getMessage, Some(ioe)))
          .ensuring(ZIO.attempt(inputStream.close()).ignore)
      )

  override def getBlobFilename(blobKey: String): ZIO[Any, PipelineError, Option[String]] = {
    ZIO.attemptBlocking {
      val statObjectResponse = minioClient.statObject(
        StatObjectArgs.builder().bucket(bucket).`object`(blobKey).build()
      )
      val metadata = statObjectResponse.userMetadata()
      val exactKey = "x-amz-meta-original-filename"
      Option(metadata.get(exactKey)).orElse {
        metadata.asScala.collectFirst {
          case (k, v) if k.toLowerCase.contains("original-filename") => v
        }
      }
    }
  }
    .mapError(error => PipelineError.BlobStoreError(error.getMessage, Some(error)))

  override def getBlobContentType(blobKey: String): ZIO[Any, PipelineError, Option[String]] = {
    ZIO.attemptBlocking {
      val statObjectResponse = minioClient.statObject(
        StatObjectArgs.builder().bucket(bucket).`object`(blobKey).build()
      )
      val metadata = statObjectResponse.userMetadata()
      val exactKey = "x-amz-meta-content-type"
      Option(metadata.get(exactKey)).orElse {
        metadata.asScala.collectFirst {
          case (k, v) if k.toLowerCase.contains("content-type") => v
        }
      }.orElse {
        Option(statObjectResponse.contentType())
      }
    }
  }
    .mapError(error => PipelineError.BlobStoreError(error.getMessage, Some(error)))

  override def deleteBlob(blobKey: String): ZIO[Any, PipelineError, Unit] = {
    ZIO
      .attemptBlocking(minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).`object`(blobKey).build()))
      .unit
  }
    .mapError(error => PipelineError.BlobStoreError(error.getMessage, Some(error)))

  override def listAllBlobs(): ZIO[Any, PipelineError, List[BlobInfo]] = {
    ZIO.attemptBlocking {
      val objects = minioClient.listObjects(
        ListObjectsArgs.builder().bucket(bucket).recursive(true).build()
      )
      objects.asScala.toList.flatMap { resultItem =>
        try {
          val item       = resultItem.get()
          val objectName = item.objectName()
          val stat       = minioClient.statObject(
            StatObjectArgs.builder().bucket(bucket).`object`(objectName).build()
          )
          val metadata = stat.userMetadata()
          val filename = {
            val exactKey = "x-amz-meta-original-filename"
            Option(metadata.get(exactKey)).orElse {
              metadata.asScala.collectFirst {
                case (k, v) if k.toLowerCase.contains("original-filename") => v
              }
            }
          }
          val contentType = {
            val exactKey = "x-amz-meta-content-type"
            Option(metadata.get(exactKey)).orElse {
              metadata.asScala.collectFirst {
                case (k, v) if k.toLowerCase.contains("content-type") => v
              }
            }.orElse(Option(stat.contentType()))
          }
          Some(
            BlobInfo(
              key = objectName,
              filename = filename,
              contentType = contentType,
              size = Option(stat.size()),
              created = Option(stat.lastModified()).map(_.toInstant)
            )
          )
        } catch {
          case _: Throwable => None
        }
      }
    }
  }
    .mapError(error => PipelineError.BlobStoreError(error.getMessage, Some(error)))

  override def healthCheck(): Task[HealthStatus] =
    (ZIO.attemptBlocking(minioClient.listBuckets()) catchAll { t =>
      ZIO.fail(t)

    }).map { _ =>
      HealthStatus.Healthy(
        serviceName = s"MinIO($endpoint)",
        checkedAt = Instant.now(),
        details = Map("endpoint" -> endpoint, "bucket" -> bucket)
      )
    }.catchAll { error =>
      ZIO.succeed(
        HealthStatus.Unhealthy(
          serviceName = s"MinIO($endpoint)",
          checkedAt = Instant.now(),
          error = error.getMessage,
          details = Map("endpoint" -> endpoint, "bucket" -> bucket)
        )
      )
    }

  private def uploadBlobWithMetadata(
    key: String,
    content: Array[Byte],
    contentType: String,
    metadata: java.util.Map[String, String]
  ): Task[Unit] =
    ZIO.attemptBlocking {
      val inputStream = ByteArrayInputStream(content)
      minioClient.putObject(
        PutObjectArgs
          .builder()
          .bucket(bucket)
          .`object`(key)
          .stream(inputStream, content.length, -1)
          .contentType(contentType)
          .userMetadata(metadata)
          .build()
      )
      inputStream.close()
    }

}

object MinioAdapter {
  val layer: ZLayer[BlobStoreAdapterConfig.MinIO, Throwable, BlobStorePort] =
    ZLayer.scoped {
      for {
        config <- ZIO.service[BlobStoreAdapterConfig.MinIO]
        client <- ZIO.acquireRelease {
                    ZIO.attemptBlocking {
                      MinioClient
                        .builder()
                        .endpoint(config.host, config.port, false)
                        .credentials(config.accessKey, config.secretKey)
                        .build()
                    }
                  } { _ =>
                    // MinioClient does not have a close mechanism, but if it did, it would be called here.
                    ZIO.unit
                  }
      } yield new MinioAdapter(client, s"${config.host}:${config.port}", config.bucket)
    }
}
