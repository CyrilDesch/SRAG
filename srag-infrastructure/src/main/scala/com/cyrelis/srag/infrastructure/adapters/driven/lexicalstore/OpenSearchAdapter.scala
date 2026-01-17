package com.cyrelis.srag.infrastructure.adapters.driven.lexicalstore

import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.{Base64, UUID}

import scala.concurrent.duration.*

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.ports.driven.storage.{DocumentInfo, LexicalStorePort}
import com.cyrelis.srag.application.types.{HealthStatus, LexicalSearchResult, VectorStoreFilter}
import com.cyrelis.srag.infrastructure.config.LexicalStoreAdapterConfig
import com.cyrelis.srag.infrastructure.resilience.ErrorMapper
import io.circe.{Decoder, parser, *}
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.client4.{Backend, *}
import sttp.model.MediaType
import zio.*

final class OpenSearchAdapter(config: LexicalStoreAdapterConfig.OpenSearch) extends LexicalStorePort {

  private val httpClient: HttpClient =
    HttpClient
      .newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .build()

  private val serviceName = s"OpenSearch(${config.index})"

  private val baseUrl  = config.url.stripSuffix("/")
  private val indexUrl = s"$baseUrl/${config.index}"

  private val maybeAuth: Option[(String, String)] =
    for {
      username <- config.username
      password <- config.password
    } yield (username, password)

  private val authHeaders: Map[String, String] =
    maybeAuth match
      case Some((username, password)) =>
        val token = Base64.getEncoder.encodeToString(s"$username:$password".getBytes(StandardCharsets.UTF_8))
        Map("Authorization" -> s"Basic $token")
      case None => Map.empty

  private def metadataToJson(metadata: Map[String, String]): Json =
    Json.obj(metadata.map { case (k, v) => (k, Json.fromString(v)) }.toSeq*)

  private def ensureIndexExists(backend: Backend[Task]): Task[Unit] =
    for {
      headResp <- basicRequest
                    .head(uri"$indexUrl")
                    .headers(authHeaders)
                    .response(asStringAlways)
                    .send(backend)
      _ <- if (headResp.code.isSuccess) ZIO.unit
           else {
             val settings = Json.obj(
               "settings" -> Json.obj(
                 "index" -> Json.obj(
                   "number_of_shards"   -> Json.fromInt(1),
                   "number_of_replicas" -> Json.fromInt(0)
                 )
               ),
               "mappings" -> Json.obj(
                 "dynamic_templates" -> Json.arr(
                   Json.obj(
                     "metadata_keywords" -> Json.obj(
                       "path_match" -> Json.fromString("metadata.*"),
                       "mapping"    -> Json.obj("type" -> Json.fromString("keyword"))
                     )
                   )
                 ),
                 "properties" -> Json.obj(
                   "transcript_id" -> Json.obj("type" -> Json.fromString("keyword")),
                   "segment_index" -> Json.obj("type" -> Json.fromString("integer")),
                   "text"          -> Json.obj("type" -> Json.fromString("text")),
                   "metadata"      -> Json.obj("type" -> Json.fromString("object"))
                 )
               )
             )
             basicRequest
               .put(uri"$indexUrl")
               .headers(authHeaders)
               .contentType(MediaType.ApplicationJson)
               .body(settings.noSpaces)
               .response(asStringAlways)
               .send(backend)
               .flatMap { resp =>
                 if (resp.code.isSuccess) ZIO.unit
                 else
                   ZIO.fail(new RuntimeException(s"OpenSearch index creation failed (${resp.code.code}): ${resp.body}"))
               }
           }
    } yield ()

  override def indexSegments(
    transcriptId: UUID,
    segments: List[(Int, String)],
    metadata: Map[String, String]
  ): ZIO[Any, PipelineError, Unit] =
    ErrorMapper.mapLexicalStoreError {
      if (segments.isEmpty) ZIO.unit
      else
        ZIO.scoped {
          for {
            backend     <- HttpClientZioBackend.scopedUsingClient(httpClient)
            _           <- ensureIndexExists(backend)
            metadataJson = metadataToJson(metadata)
            bulkBody     = segments.map { case (index, text) =>
                         val action = Json
                           .obj(
                             "index" -> Json.obj(
                               "_index" -> Json.fromString(config.index),
                               "_id"    -> Json.fromString(s"${transcriptId.toString}_$index")
                             )
                           )
                           .noSpaces

                         val doc = Json
                           .obj(
                             "transcript_id" -> Json.fromString(transcriptId.toString),
                             "segment_index" -> Json.fromInt(index),
                             "text"          -> Json.fromString(text),
                             "metadata"      -> metadataJson
                           )
                           .noSpaces

                         s"$action\n$doc"
                       }
                         .mkString("", "\n", "\n")
            response <- basicRequest
                          .post(uri"$baseUrl/_bulk")
                          .headers(authHeaders)
                          .contentType(MediaType("application", "x-ndjson"))
                          .body(bulkBody)
                          .response(asStringAlways)
                          .send(backend)
            _ <- ZIO
                   .when(!response.code.isSuccess)(
                     ZIO.fail(
                       new RuntimeException(s"OpenSearch bulk index failed (${response.code.code}): ${response.body}")
                     )
                   )
          } yield ()
        }
    }

  override def deleteTranscript(transcriptId: UUID): ZIO[Any, PipelineError, Unit] =
    ErrorMapper.mapLexicalStoreError {
      ZIO.scoped {
        for {
          backend  <- HttpClientZioBackend.scopedUsingClient(httpClient)
          _        <- ensureIndexExists(backend)
          queryBody = Json
                        .obj(
                          "query" -> Json.obj(
                            "term" -> Json.obj(
                              "transcript_id" -> Json.obj(
                                "value" -> Json.fromString(transcriptId.toString)
                              )
                            )
                          )
                        )
                        .noSpaces
          response <- basicRequest
                        .post(uri"$indexUrl/_delete_by_query")
                        .headers(authHeaders)
                        .contentType(MediaType.ApplicationJson)
                        .body(queryBody)
                        .response(asStringAlways)
                        .send(backend)
          _ <- ZIO
                 .when(!response.code.isSuccess)(
                   ZIO.fail(
                     new RuntimeException(
                       s"OpenSearch delete_by_query failed (${response.code.code}): ${response.body}"
                     )
                   )
                 )
        } yield ()
      }
    }

  private final case class OpenSearchHitSource(
    transcript_id: String,
    segment_index: Int,
    text: String,
    metadata: Option[Map[String, String]]
  ) derives Decoder

  private final case class OpenSearchHit(_id: String, _score: Double, _source: OpenSearchHitSource) derives Decoder

  private final case class OpenSearchHits(hits: List[OpenSearchHit]) derives Decoder

  private final case class OpenSearchSearchResponse(hits: OpenSearchHits) derives Decoder

  override def search(
    queryText: String,
    limit: Int,
    filter: Option[VectorStoreFilter]
  ): ZIO[Any, PipelineError, List[LexicalSearchResult]] =
    ErrorMapper.mapLexicalStoreError {
      ZIO.scoped {
        for {
          backend <- HttpClientZioBackend.scopedUsingClient(httpClient)
          _       <- ensureIndexExists(backend)
          baseMust = List(
                       Json.obj(
                         "match" -> Json.obj(
                           "text" -> Json.obj(
                             "query"            -> Json.fromString(queryText),
                             "operator"         -> Json.fromString("or"),
                             "zero_terms_query" -> Json.fromString("all")
                           )
                         )
                       )
                     )
          filterTerms = filter
                          .map(_.metadata.map { case (k, v) =>
                            Json.obj(
                              "term" -> Json.obj(
                                s"metadata.$k" -> Json.fromString(v)
                              )
                            )
                          }.toList)
                          .getOrElse(Nil)
          queryBody = Json
                        .obj(
                          "size"  -> Json.fromInt(limit),
                          "query" -> Json.obj(
                            "bool" -> Json.obj(
                              "must"   -> Json.fromValues(baseMust),
                              "filter" -> Json.fromValues(filterTerms)
                            )
                          )
                        )
                        .noSpaces
          response <- basicRequest
                        .post(uri"$indexUrl/_search")
                        .headers(authHeaders)
                        .contentType(MediaType.ApplicationJson)
                        .body(queryBody)
                        .response(asStringAlways)
                        .send(backend)
          _ <- ZIO
                 .when(!response.code.isSuccess)(
                   ZIO.fail(new RuntimeException(s"OpenSearch search failed (${response.code.code}): ${response.body}"))
                 )
          parsed <- ZIO
                      .fromEither(parser.decode[OpenSearchSearchResponse](response.body))
                      .mapError(err => new RuntimeException(s"Failed to decode OpenSearch response: ${err.getMessage}"))
          results = parsed.hits.hits.map { hit =>
                      LexicalSearchResult(
                        transcriptId = UUID.fromString(hit._source.transcript_id),
                        segmentIndex = hit._source.segment_index,
                        score = hit._score,
                        text = hit._source.text,
                        metadata = hit._source.metadata.getOrElse(Map.empty)
                      )
                    }
        } yield results
      }
    }

  override def listAllDocuments(): ZIO[Any, PipelineError, List[DocumentInfo]] =
    ErrorMapper.mapLexicalStoreError {
      ZIO.scoped {
        for {
          backend  <- HttpClientZioBackend.scopedUsingClient(httpClient)
          _        <- ensureIndexExists(backend)
          queryBody = Json
                        .obj(
                          "size"  -> Json.fromInt(10000),
                          "query" -> Json.obj("match_all" -> Json.obj())
                        )
                        .noSpaces
          response <- basicRequest
                        .post(uri"$indexUrl/_search")
                        .headers(authHeaders)
                        .contentType(MediaType.ApplicationJson)
                        .body(queryBody)
                        .response(asStringAlways)
                        .send(backend)
          _ <- ZIO
                 .when(!response.code.isSuccess)(
                   ZIO.fail(new RuntimeException(s"OpenSearch search failed (${response.code.code}): ${response.body}"))
                 )
          parsed <- ZIO
                      .fromEither(parser.decode[OpenSearchSearchResponse](response.body))
                      .mapError(err => new RuntimeException(s"Failed to decode OpenSearch response: ${err.getMessage}"))
          documents = parsed.hits.hits.map { hit =>
                        DocumentInfo(
                          id = hit._id,
                          transcriptId = Some(UUID.fromString(hit._source.transcript_id)),
                          segmentIndex = Some(hit._source.segment_index),
                          text = Some(hit._source.text),
                          metadata = hit._source.metadata
                        )
                      }
        } yield documents
      }
    }

  override def healthCheck(): Task[HealthStatus] = {
    val now = Instant.now()

    val check = ZIO.scoped {
      for {
        backend  <- HttpClientZioBackend.scopedUsingClient(httpClient)
        response <- basicRequest
                      .get(uri"$baseUrl/_cluster/health")
                      .headers(authHeaders)
                      .readTimeout(5.seconds)
                      .response(asStringAlways)
                      .send(backend)
      } yield {
        if (response.code.isSuccess) {
          HealthStatus.Healthy(
            serviceName = serviceName,
            checkedAt = now,
            details = Map(
              "url"   -> config.url,
              "index" -> config.index
            )
          )
        } else {
          HealthStatus.Unhealthy(
            serviceName = serviceName,
            checkedAt = now,
            error = s"HTTP ${response.code.code}",
            details = Map(
              "url"        -> config.url,
              "index"      -> config.index,
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
            "url"   -> config.url,
            "index" -> config.index
          )
        )
      )
    }
  }
}

object OpenSearchAdapter {
  def apply(config: LexicalStoreAdapterConfig.OpenSearch): OpenSearchAdapter =
    new OpenSearchAdapter(config)
}
