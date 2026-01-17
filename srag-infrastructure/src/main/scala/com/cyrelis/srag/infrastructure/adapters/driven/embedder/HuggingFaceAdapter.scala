package com.cyrelis.srag.infrastructure.adapters.driven.embedder

import java.net.http.HttpClient
import java.time.Instant

import scala.concurrent.duration.*

import com.cyrelis.srag.application.ports.driven.embedding.EmbedderPort
import com.cyrelis.srag.application.types.HealthStatus
import com.cyrelis.srag.domain.transcript.Transcript
import com.cyrelis.srag.infrastructure.config.EmbedderAdapterConfig
import com.cyrelis.srag.infrastructure.resilience.ErrorMapper
import io.circe.Codec
import io.circe.parser.*
import io.circe.syntax.*
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.MediaType
import zio.*

final case class HuggingFaceResponse(text: String, vector: List[Float], dim: Int) derives Codec

class HuggingFaceAdapter(config: EmbedderAdapterConfig.HuggingFace) extends EmbedderPort {

  private val httpClient: HttpClient =
    HttpClient
      .newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .build()

  private val serviceName = s"HuggingFaceEmbedder(${config.model})"

  override def embed(
    transcript: Transcript
  ): ZIO[Any, com.cyrelis.srag.application.errors.PipelineError, List[(String, Array[Float])]] =
    ErrorMapper.mapEmbeddingError {
      val chunks = TextChunker.chunkText(transcript.text, 1000)

      if chunks.isEmpty then ZIO.succeed(List.empty)
      else
        ZIO.foreachPar(chunks) { chunk =>
          for {
            response  <- makeEmbeddingRequest(chunk)
            embedding <- parseEmbeddingResponse(response)
          } yield (chunk, embedding)
        }
    }

  override def embedQuery(
    query: String
  ): ZIO[Any, com.cyrelis.srag.application.errors.PipelineError, Array[Float]] =
    ErrorMapper.mapEmbeddingError {
      for {
        response  <- makeEmbeddingRequest(query)
        embedding <- parseEmbeddingResponse(response)
      } yield embedding
    }

  protected def makeEmbeddingRequest(text: String): Task[Response[String]] = {
    val url = uri"${config.apiUrl}/vectors"

    val request = basicRequest
      .post(url)
      .contentType(MediaType.ApplicationJson)
      .body(Map("text" -> text).asJson.noSpaces)
      .response(asStringAlways)

    ZIO.scoped {
      for {
        backend  <- HttpClientZioBackend.scopedUsingClient(httpClient)
        response <- request.send(backend)
      } yield response
    }
  }

  private def parseEmbeddingResponse(response: Response[String]): Task[Array[Float]] =
    if (response.code.isSuccess) {
      decode[HuggingFaceResponse](response.body) match {
        case Right(huggingFaceResponse) =>
          ZIO.succeed(huggingFaceResponse.vector.toArray)
        case Left(error) =>
          ZIO.fail(
            new RuntimeException(
              s"Invalid JSON from HuggingFace API (status ${response.code.code}): ${error.getMessage}"
            )
          )
      }
    } else {
      ZIO.fail(
        new RuntimeException(
          s"HuggingFace API error (status ${response.code.code}): ${response.body}"
        )
      )
    }

  private def baseDetails: Map[String, String] = Map(
    "provider" -> "huggingface",
    "model"    -> config.model,
    "api_url"  -> config.apiUrl
  )

  override def healthCheck(): Task[HealthStatus] = {
    val now = Instant.now()

    val check = for {
      backend  <- HttpClientZioBackend()
      response <- basicRequest
                    .get(uri"${config.apiUrl}/.well-known/ready")
                    .readTimeout(5.seconds)
                    .send(backend)
      _ <- backend.close()
    } yield {
      if (response.code.isSuccess) {
        HealthStatus.Healthy(
          serviceName = serviceName,
          checkedAt = now,
          details = baseDetails + ("status" -> "connected")
        )
      } else {
        HealthStatus.Unhealthy(
          serviceName = serviceName,
          checkedAt = now,
          error = s"HTTP ${response.code.code}",
          details = baseDetails + ("response_code" -> response.code.code.toString)
        )
      }
    }

    check.catchAll { error =>
      ZIO.succeed(error match {
        case _: java.net.ConnectException =>
          HealthStatus.Unhealthy(
            serviceName = serviceName,
            checkedAt = now,
            error = s"Connection failed: ${error.getMessage}",
            details = baseDetails + ("error_type" -> "connection")
          )
        case _: sttp.client4.SttpClientException.TimeoutException =>
          HealthStatus.Timeout(
            serviceName = serviceName,
            checkedAt = now,
            timeoutMs = 5000
          )
        case _ =>
          HealthStatus.Unhealthy(
            serviceName = serviceName,
            checkedAt = now,
            error = s"Unexpected error: ${error.getMessage}",
            details = baseDetails + ("error_type" -> "unexpected")
          )
      })
    }
  }
}

object HuggingFaceAdapter {
  def apply(config: EmbedderAdapterConfig.HuggingFace): HuggingFaceAdapter =
    new HuggingFaceAdapter(config)
}
