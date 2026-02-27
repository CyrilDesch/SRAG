package com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.endpoint

import java.util.UUID

import com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.main.*
import sttp.capabilities.zio.ZioStreams
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import zio.stream.ZStream

object MainEndpoints {

  val health: PublicEndpoint[Unit, String, List[HealthStatusRestDto], Any] =
    sttp.tapir.endpoint.get
      .in("health")
      .out(jsonBody[List[HealthStatusRestDto]])
      .errorOut(stringBody)
      .description("Health check endpoint")

  val ingestAudioMultipart: PublicEndpoint[AudioIngestMultipartDto, String, JobAcceptedRestDto, Any] =
    sttp.tapir.endpoint.post
      .in("api" / "v1" / "ingest" / "audio")
      .in(multipartBody[AudioIngestMultipartDto])
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobAcceptedRestDto])
      .errorOut(stringBody)
      .description("Ingest audio file directly (multipart/form-data)")

  val ingestText: PublicEndpoint[TextIngestRestDto, String, JobAcceptedRestDto, Any] =
    sttp.tapir.endpoint.post
      .in("api" / "v1" / "ingest" / "text")
      .in(jsonBody[TextIngestRestDto])
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobAcceptedRestDto])
      .errorOut(stringBody)
      .description("Ingest raw text content")

  val jobStatus: PublicEndpoint[String, String, JobStatusRestDto, Any] =
    sttp.tapir.endpoint.get
      .in("api" / "v1" / "jobs" / path[String]("jobId"))
      .out(jsonBody[JobStatusRestDto])
      .errorOut(stringBody)
      .description("Get ingestion job status by job id")

  val transcripts
    : PublicEndpoint[(TranscriptsQueryRestDto, sttp.model.QueryParams), String, List[TranscriptRestDto], Any] =
    sttp.tapir.endpoint.get
      .in("api" / "v1" / "transcripts")
      .in(sttp.tapir.query[Option[String]]("sortBy").description("createdAt|metadata (default: createdAt)"))
      .in(sttp.tapir.query[Option[String]]("metadataSort").description("Required when sortBy=metadata"))
      .in(sttp.tapir.query[Option[String]]("order").description("asc|desc (default: asc)"))
      .in(
        sttp.tapir
          .query[List[String]]("metadata")
          .description(
            "Repeatable: key=value; e.g., metadata=project=alpha&metadata=userId=42"
          )
      )
      .mapInTo[TranscriptsQueryRestDto]
      .in(queryParams)
      .out(jsonBody[List[TranscriptRestDto]])
      .errorOut(stringBody)
      .description(
        """
        List transcripts.

        Filtering:
        - metadata=<key>=<value> (repeatable) OR metadata.<key>=<value>

        Sorting:
        - sortBy=createdAt|metadata (default: createdAt)
        - metadataSort=<key> (required when sortBy=metadata)
        - order=asc|desc (default: asc)
        """.stripMargin
      )

  val getFile: PublicEndpoint[UUID, String, (String, String, ZStream[Any, Throwable, Byte]), ZioStreams] =
    sttp.tapir.endpoint.get
      .in("api" / "v1" / "files" / path[UUID]("blobKey"))
      .out(header[String]("Content-Disposition"))
      .out(header[String]("Content-Type"))
      .out(
        streamBody(ZioStreams)(
          Schema.binary[Byte],
          CodecFormat.OctetStream(),
          None
        )
      )
      .errorOut(stringBody)
      .description("Get file by blob key (streaming with Content-Disposition and Content-Type headers)")

  val query: PublicEndpoint[QueryRequestDto, String, List[QueryResponseDto], Any] =
    sttp.tapir.endpoint.post
      .in("api" / "v1" / "query")
      .in(jsonBody[QueryRequestDto])
      .out(jsonBody[List[QueryResponseDto]])
      .errorOut(stringBody)
      .description(
        """
        Search and retrieve context segments using hybrid semantic + lexical search with reranking.
        
        Request:
        - query: Search query text (required)
        - limit: Maximum number of results (default: 5, optional)
        - metadata: Optional metadata filters (key-value pairs)
        
        Returns ranked list of context segments with scores.
        """.stripMargin
      )

  def all: List[PublicEndpoint[?, ?, ?, ?]] =
    List(health, ingestAudioMultipart, ingestText, jobStatus, transcripts, getFile, query)
}
