package com.cyrelis.srag.infrastructure.migration

import java.net.http.HttpClient

import scala.concurrent.duration.*

import com.cyrelis.srag.infrastructure.config.VectorStoreAdapterConfig
import io.circe.Codec
import io.circe.syntax.*
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.MediaType
import zio.*

final case class QdrantCollectionCreateRequest(vectors: QdrantVectorsCreateConfig) derives Codec

final case class QdrantVectorsCreateConfig(size: Int, distance: String = "Cosine") derives Codec

trait VectorStoreInitializer {
  def initialize(): Task[Unit]
}

object VectorStoreInitializer {

  final class QdrantInitializer(config: VectorStoreAdapterConfig.Qdrant) extends VectorStoreInitializer {

    private val httpClient: HttpClient =
      HttpClient
        .newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .build()

    private val baseUrl = config.url.stripSuffix("/")

    private def buildHeaders(): Map[String, String] =
      if (config.apiKey.nonEmpty) Map("api-key" -> config.apiKey) else Map.empty

    override def initialize(): Task[Unit] =
      ZIO.logInfo(s"Initializing Qdrant collection: ${config.collection}") *>
        createCollectionIfNotExists() *>
        ZIO.logInfo(s"Qdrant collection '${config.collection}' is ready")

    private def checkCollectionExists(): Task[Boolean] =
      ZIO.scoped {
        for {
          backend  <- HttpClientZioBackend.scopedUsingClient(httpClient)
          headers   = buildHeaders()
          url       = uri"$baseUrl/collections/${config.collection}"
          request   = basicRequest.get(url).headers(headers).readTimeout(5.seconds).response(asStringAlways)
          response <- request.send(backend)
          exists   <- if (response.code.code == 200) ZIO.succeed(true)
                    else if (response.code.code == 404) ZIO.succeed(false)
                    else
                      ZIO.fail(
                        new RuntimeException(
                          s"Failed to check collection existence (status ${response.code.code}): ${response.body}"
                        )
                      )
        } yield exists
      }

    private def createCollectionIfNotExists(): Task[Unit] =
      for {
        exists <- checkCollectionExists()
        _      <-
          if (exists) {
            ZIO.logInfo(s"Qdrant collection '${config.collection}' already exists, skipping creation")
          } else {
            createCollection()
          }
      } yield ()

    private def createCollection(): Task[Unit] =
      ZIO.scoped {
        for {
          backend         <- HttpClientZioBackend.scopedUsingClient(httpClient)
          collectionConfig = QdrantCollectionCreateRequest(
                               QdrantVectorsCreateConfig(size = 384, distance = "Cosine")
                             )
          requestBody = collectionConfig.asJson.noSpaces
          url         = uri"$baseUrl/collections/${config.collection}"
          headers     = buildHeaders()
          request     = basicRequest
                      .put(url)
                      .headers(headers)
                      .contentType(MediaType.ApplicationJson)
                      .body(requestBody)
                      .readTimeout(10.seconds)
                      .response(asStringAlways)
          response <- request.send(backend)
          _        <-
            if (response.code.isSuccess) {
              ZIO.logInfo(s"Created Qdrant collection '${config.collection}'")
            } else if (response.code.code == 400) {
              ZIO.logWarning(
                s"Failed to create Qdrant collection (status ${response.code.code}): ${response.body}. It may already exist."
              )
            } else {
              ZIO.fail(
                new RuntimeException(
                  s"Failed to create Qdrant collection (status ${response.code.code}): ${response.body}"
                )
              )
            }
        } yield ()
      }
  }

  def layer: ZLayer[VectorStoreAdapterConfig, Nothing, VectorStoreInitializer] =
    ZLayer.fromFunction { (config: VectorStoreAdapterConfig) =>
      config match {
        case cfg: VectorStoreAdapterConfig.Qdrant => new QdrantInitializer(cfg)
      }
    }
}
