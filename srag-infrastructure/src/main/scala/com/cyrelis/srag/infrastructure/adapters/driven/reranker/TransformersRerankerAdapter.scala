package com.cyrelis.srag.infrastructure.adapters.driven.reranker

import java.net.http.HttpClient
import java.time.Instant

import scala.collection.mutable
import scala.concurrent.duration.*

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.ports.driven.reranker.RerankerPort
import com.cyrelis.srag.application.types.{HealthStatus, RerankerCandidate, RerankerResult}
import com.cyrelis.srag.infrastructure.config.RerankerAdapterConfig
import com.cyrelis.srag.infrastructure.resilience.ErrorMapper
import io.circe.Codec
import io.circe.syntax.*
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.MediaType
import zio.*

final case class TransformersRerankRequest(query: String, documents: List[String]) derives Codec

final case class TransformersRerankDocumentScore(document: String, score: Double) derives Codec

final case class TransformersRerankResponse(query: String, scores: Option[List[TransformersRerankDocumentScore]])
    derives Codec

class TransformersRerankerAdapter(config: RerankerAdapterConfig.Transformers) extends RerankerPort {

  private val httpClient: HttpClient =
    HttpClient
      .newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .build()

  private val serviceName = s"TransformersReranker(${config.model})"

  private val baseUrl = config.apiUrl.stripSuffix("/")

  override def rerank(
    query: String,
    candidates: List[RerankerCandidate],
    topK: Int
  ): ZIO[Any, PipelineError, List[RerankerResult]] =
    ErrorMapper.mapRerankerError {
      if (candidates.isEmpty) ZIO.succeed(List.empty)
      else
        ZIO.scoped {
          val limited          = candidates.take(topK)
          val candidatesByText = mutable.Map.from(
            limited.groupBy(_.text)
          )
          for {
            backend    <- HttpClientZioBackend.scopedUsingClient(httpClient)
            requestBody = TransformersRerankRequest(
                            query = query,
                            documents = limited.map(_.text)
                          ).asJson.noSpaces
            request = basicRequest
                        .post(uri"$baseUrl/rerank")
                        .contentType(MediaType.ApplicationJson)
                        .body(requestBody)
                        .response(asStringAlways)
            response <- request.send(backend)
            _        <- ZIO
                   .when(!response.code.isSuccess)(
                     ZIO.fail(
                       new RuntimeException(s"Reranker request failed (${response.code.code}): ${response.body}")
                     )
                   )
            rerankResponse <-
              ZIO
                .fromEither(io.circe.parser.decode[TransformersRerankResponse](response.body))
                .mapError(err => new RuntimeException(s"Failed to decode reranker response: ${err.getMessage}"))
            results = rerankResponse.scores.toList.flatten.flatMap { item =>
                        candidatesByText.get(item.document).flatMap {
                          case head :: tail =>
                            candidatesByText.update(item.document, tail)
                            Some(RerankerResult(candidate = head, score = item.score))
                          case Nil =>
                            None
                        }
                      }
          } yield results
        }
    }

  override def healthCheck(): Task[HealthStatus] = {
    val now = Instant.now()

    val check = ZIO.scoped {
      for {
        backend  <- HttpClientZioBackend.scopedUsingClient(httpClient)
        response <- basicRequest
                      .get(uri"$baseUrl/.well-known/ready")
                      .readTimeout(5.seconds)
                      .send(backend)
      } yield {
        if (response.code.isSuccess) {
          HealthStatus.Healthy(
            serviceName = serviceName,
            checkedAt = now,
            details = Map(
              "url"   -> config.apiUrl,
              "model" -> config.model
            )
          )
        } else {
          HealthStatus.Unhealthy(
            serviceName = serviceName,
            checkedAt = now,
            error = s"HTTP ${response.code.code}",
            details = Map(
              "url"        -> config.apiUrl,
              "model"      -> config.model,
              "statusCode" -> response.code.code.toString
            )
          )
        }
      }
    }

    check.catchAll { error =>
      ZIO.succeed(
        HealthStatus.Unhealthy(
          serviceName = serviceName,
          checkedAt = now,
          error = s"Unexpected error: ${error.getMessage}",
          details = Map(
            "url"   -> config.apiUrl,
            "model" -> config.model
          )
        )
      )
    }
  }
}

object TransformersRerankerAdapter {
  def apply(config: RerankerAdapterConfig.Transformers): TransformersRerankerAdapter =
    new TransformersRerankerAdapter(config)
}
