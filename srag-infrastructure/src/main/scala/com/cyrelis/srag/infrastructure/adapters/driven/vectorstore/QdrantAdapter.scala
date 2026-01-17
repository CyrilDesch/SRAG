package com.cyrelis.srag.infrastructure.adapters.driven.vectorstore

import java.net.http.HttpClient
import java.time.Instant
import java.util.UUID

import scala.concurrent.duration.*

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.ports.driven.storage.{VectorInfo, VectorStorePort}
import com.cyrelis.srag.application.types.{HealthStatus, VectorSearchResult, VectorStoreFilter}
import com.cyrelis.srag.infrastructure.config.VectorStoreAdapterConfig
import com.cyrelis.srag.infrastructure.resilience.ErrorMapper
import io.circe.Codec
import io.circe.parser.*
import io.circe.syntax.*
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.MediaType
import zio.*

final case class QdrantPoint(id: String, vector: List[Float], payload: Map[String, String]) derives Codec

final case class QdrantUpsertRequest(points: List[QdrantPoint]) derives Codec

final case class QdrantFilterMatch(value: String) derives Codec

final case class QdrantFilterCondition(key: String, `match`: QdrantFilterMatch) derives Codec

final case class QdrantFilter(must: List[QdrantFilterCondition]) derives Codec

final case class QdrantSearchRequest(
  vector: List[Float],
  limit: Int,
  filter: Option[QdrantFilter] = None,
  with_payload: Option[Boolean] = Some(true)
) derives Codec

final case class QdrantSearchResult(id: String, score: Double, payload: Option[Map[String, String]] = None)
    derives Codec

final case class QdrantSearchResponse(result: List[QdrantSearchResult]) derives Codec

final class QdrantAdapter(config: VectorStoreAdapterConfig.Qdrant) extends VectorStorePort {

  private val httpClient: HttpClient =
    HttpClient
      .newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .build()

  private val serviceName = s"Qdrant(${config.collection})"

  private val baseUrl = config.url.stripSuffix("/")

  private def buildHeaders(): Map[String, String] =
    if (config.apiKey.nonEmpty) Map("api-key" -> config.apiKey) else Map.empty

  override def upsertEmbeddings(
    transcriptId: UUID,
    vectors: List[Array[Float]],
    metadata: Map[String, String]
  ): ZIO[Any, PipelineError, Unit] =
    ErrorMapper.mapVectorStoreError {
      if (vectors.isEmpty) ZIO.unit
      else
        ZIO.scoped {
          for {
            backend <- HttpClientZioBackend.scopedUsingClient(httpClient)
            points   = vectors.zipWithIndex.map { case (vector, index) =>
                       val basePayload = Map(
                         "transcript_id" -> transcriptId.toString,
                         "index"         -> index.toString
                       )
                       val fullPayload = basePayload ++ metadata
                       QdrantPoint(
                         id = UUID.randomUUID().toString,
                         vector = vector.toList,
                         payload = fullPayload
                       )
                     }
            requestBody = QdrantUpsertRequest(points).asJson.noSpaces
            url         = uri"$baseUrl/collections/${config.collection}/points"
            headers     = buildHeaders()
            request     = basicRequest
                        .put(url)
                        .headers(headers)
                        .contentType(MediaType.ApplicationJson)
                        .body(requestBody)
                        .response(asStringAlways)
            response <- request.send(backend)
            _        <- ZIO
                   .when(!response.code.isSuccess)(
                     ZIO.fail(
                       new RuntimeException(
                         s"Qdrant upsert failed (status ${response.code.code}): ${response.body}"
                       )
                     )
                   )
          } yield ()
        }
    }

  override def searchSimilar(
    queryVector: Array[Float],
    limit: Int,
    filter: Option[VectorStoreFilter] = None
  ): ZIO[Any, PipelineError, List[VectorSearchResult]] =
    ErrorMapper.mapVectorStoreError {
      ZIO.scoped {
        for {
          backend      <- HttpClientZioBackend.scopedUsingClient(httpClient)
          qdrantFilter  = filter.map(buildQdrantFilter)
          searchRequest = QdrantSearchRequest(
                            vector = queryVector.toList,
                            limit = limit,
                            filter = qdrantFilter
                          )
          requestBody = searchRequest.asJson.noSpaces
          url         = uri"$baseUrl/collections/${config.collection}/points/search"
          headers     = buildHeaders()
          request     = basicRequest
                      .post(url)
                      .headers(headers)
                      .contentType(MediaType.ApplicationJson)
                      .body(requestBody)
                      .response(asStringAlways)
          response <- request.send(backend)
          _        <- ZIO
                 .when(!response.code.isSuccess)(
                   ZIO.fail(
                     new RuntimeException(
                       s"Qdrant search failed (status ${response.code.code}): ${response.body}"
                     )
                   )
                 )
          searchResponse <- ZIO
                              .fromEither(decode[QdrantSearchResponse](response.body))
                              .mapError(err =>
                                new RuntimeException(
                                  s"Failed to parse Qdrant search response: ${err.getMessage}"
                                )
                              )
          results = searchResponse.result.flatMap { qdrantResult =>
                      qdrantResult.payload match {
                        case Some(payload) =>
                          val transcriptId = payload
                            .get("transcript_id")
                            .map(UUID.fromString)
                            .getOrElse(
                              throw new RuntimeException(
                                s"Missing transcript_id in search result: ${qdrantResult.id}"
                              )
                            )
                          val segmentIndex = payload
                            .get("index")
                            .map(_.toInt)
                            .getOrElse(
                              throw new RuntimeException(s"Missing index in search result: ${qdrantResult.id}")
                            )
                          Some(
                            VectorSearchResult(
                              transcriptId = transcriptId,
                              segmentIndex = segmentIndex,
                              score = qdrantResult.score
                            )
                          )
                        case None =>
                          None
                      }
                    }
        } yield results
      }
    }

  private def buildQdrantFilter(filter: VectorStoreFilter): QdrantFilter =
    QdrantFilter(
      must = filter.metadata.map { case (key, value) =>
        QdrantFilterCondition(
          key = key,
          `match` = QdrantFilterMatch(value = value)
        )
      }.toList
    )

  final case class QdrantScrollRequest(
    limit: Int,
    offset: Option[String] = None,
    with_payload: Option[Boolean] = Some(true),
    with_vector: Option[Boolean] = Some(true)
  ) derives Codec

  final case class QdrantScrollResult(
    id: String,
    payload: Option[Map[String, String]] = None,
    vector: Option[List[Float]] = None
  ) derives Codec

  final case class QdrantScrollPage(
    points: List[QdrantScrollResult],
    next_page_offset: Option[String] = None
  ) derives Codec

  final case class QdrantScrollResponse(
    result: QdrantScrollPage,
    status: Option[String] = None
  ) derives Codec

  override def listAllVectors(): ZIO[Any, PipelineError, List[VectorInfo]] =
    ErrorMapper.mapVectorStoreError {
      def scrollPage(
        backend: Backend[Task],
        headers: Map[String, String],
        offset: Option[String],
        acc: List[QdrantScrollResult]
      ): ZIO[Any, Throwable, List[QdrantScrollResult]] = {
        val scrollRequest = QdrantScrollRequest(
          limit = 100,
          offset = offset,
          with_payload = Some(true),
          with_vector = Some(true)
        )
        val requestBody = scrollRequest.asJson.noSpaces
        val url         = uri"$baseUrl/collections/${config.collection}/points/scroll"
        val request     = basicRequest
          .post(url)
          .headers(headers)
          .contentType(MediaType.ApplicationJson)
          .body(requestBody)
          .response(asStringAlways)
        for {
          response <- request.send(backend)
          _        <- ZIO
                 .when(!response.code.isSuccess)(
                   ZIO.fail(
                     new RuntimeException(
                       s"Qdrant scroll failed (status ${response.code.code}): ${response.body}"
                     )
                   )
                 )
          scrollResponse <- ZIO
                              .fromEither(decode[QdrantScrollResponse](response.body))
                              .mapError(err =>
                                new RuntimeException(
                                  s"Failed to parse Qdrant scroll response: ${err.getMessage}"
                                )
                              )
          newAcc     = acc ++ scrollResponse.result.points
          nextOffset = scrollResponse.result.next_page_offset
          result    <- if (nextOffset.isDefined) scrollPage(backend, headers, nextOffset, newAcc)
                    else ZIO.succeed(newAcc)
        } yield result
      }

      ZIO.scoped {
        for {
          backend   <- HttpClientZioBackend.scopedUsingClient(httpClient)
          headers    = buildHeaders()
          allPoints <- scrollPage(backend, headers, None, List.empty)
          vectors   <- ZIO.foreach(allPoints) { point =>
                       for {
                         transcriptId <- ZIO.succeed(
                                           point.payload
                                             .flatMap(_.get("transcript_id"))
                                             .map(UUID.fromString)
                                         )
                         indexValue   = point.payload.flatMap(_.get("index"))
                         segmentIndex = indexValue.map(_.toInt)
                         result      <- ZIO.succeed(
                                     VectorInfo(
                                       id = point.id,
                                       transcriptId = transcriptId,
                                       segmentIndex = segmentIndex,
                                       vector = point.vector,
                                       payload = point.payload
                                     )
                                   )
                       } yield result
                     }
        } yield vectors
      }
    }

  override def healthCheck(): Task[HealthStatus] = {
    val now = Instant.now()

    val check = ZIO.scoped {
      for {
        backend  <- HttpClientZioBackend.scopedUsingClient(httpClient)
        headers   = buildHeaders()
        url       = uri"$baseUrl/collections/${config.collection}"
        request   = basicRequest.get(url).headers(headers).readTimeout(5.seconds).response(asStringAlways)
        response <- request.send(backend)
        status    = if (response.code.isSuccess) {
                   HealthStatus.Healthy(
                     serviceName = serviceName,
                     checkedAt = now,
                     details = Map(
                       "url"        -> config.url,
                       "collection" -> config.collection,
                       "status"     -> "connected"
                     )
                   )
                 } else {
                   HealthStatus.Unhealthy(
                     serviceName = serviceName,
                     checkedAt = now,
                     error = s"HTTP ${response.code.code}",
                     details = Map(
                       "url"           -> config.url,
                       "collection"    -> config.collection,
                       "response_code" -> response.code.code.toString
                     )
                   )
                 }
      } yield status
    }

    check.catchAll { error =>
      ZIO.succeed(error match {
        case _: java.net.ConnectException =>
          HealthStatus.Unhealthy(
            serviceName = serviceName,
            checkedAt = now,
            error = s"Connection failed: ${error.getMessage}",
            details = Map(
              "url"        -> config.url,
              "collection" -> config.collection,
              "error_type" -> "connection"
            )
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
            details = Map(
              "url"        -> config.url,
              "collection" -> config.collection,
              "error_type" -> "unexpected"
            )
          )
      })
    }
  }
}

object QdrantAdapter {
  def apply(config: VectorStoreAdapterConfig.Qdrant): QdrantAdapter =
    new QdrantAdapter(config)
}
