package com.cyrelis.srag.infrastructure.adapters.driven.transcriber

import java.net.http.HttpClient
import java.time.Instant
import java.util.UUID

import scala.concurrent.duration.*

import com.cyrelis.srag.application.ports.driven.transcription.TranscriberPort
import com.cyrelis.srag.application.types.HealthStatus
import com.cyrelis.srag.domain.transcript.{IngestSource, LanguageCode, Transcript, Word}
import com.cyrelis.srag.infrastructure.config.TranscriberAdapterConfig
import com.cyrelis.srag.infrastructure.resilience.ErrorMapper
import io.circe.Codec
import io.circe.parser.*
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.MediaType
import zio.*

final case class WhisperResponse(
  text: String,
  language: Option[String]
) derives Codec

class WhisperAdapter(config: TranscriberAdapterConfig.Whisper) extends TranscriberPort {

  private val httpClient: HttpClient =
    HttpClient
      .newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .build()

  override def transcribe(
    audioContent: Array[Byte],
    mediaContentType: String,
    mediaFilename: String
  ): ZIO[Any, com.cyrelis.srag.application.errors.PipelineError, Transcript] = {
    val transcriptId = UUID.randomUUID()
    val now          = Instant.now()

    ErrorMapper.mapTranscriptionError {
      for {
        response <- makeWhisperRequest(audioContent, mediaContentType, mediaFilename)
        _        <-
          ZIO.logDebug(
            s"Whisper API response: status=${response.code.code}, body length=${response.body.length} chars, body preview=${
                if (response.body.length > 200) response.body.take(200) + "..." else response.body
              }"
          )
        whisperResponse <- parseWhisperResponse(response, audioContent.length, mediaContentType)
        _               <-
          ZIO.logDebug(
            s"Parsed Whisper response: text length=${whisperResponse.text.length}, language=${whisperResponse.language.getOrElse("none")}"
          )
        transcript <- if (whisperResponse.text.isEmpty) {
                        ZIO.fail(
                          new RuntimeException(
                            s"Whisper API returned empty text for transcript $transcriptId"
                          )
                        )
                      } else {
                        ZIO.succeed(
                          Transcript(
                            id = transcriptId,
                            language = whisperResponse.language.map(LanguageCode.unsafe),
                            words = List(
                              Word(
                                text = whisperResponse.text,
                                start = 0L,
                                end = 0L,
                                confidence = 0.8
                              )
                            ),
                            confidence = 0.8,
                            createdAt = now,
                            source = IngestSource.Audio,
                            metadata = Map(
                              "provider" -> "whisper",
                              "model"    -> config.modelPath
                            )
                          )
                        )
                      }
      } yield transcript
    }
  }

  protected def makeWhisperRequest(
    audioBytes: Array[Byte],
    mediaContentType: String,
    mediaFilename: String
  ): Task[Response[String]] = {
    val url = uri"${config.apiUrl}/asr".addParams(
      "encode"          -> "true",
      "task"            -> "transcribe",
      "vad_filter"      -> "false",
      "word_timestamps" -> "false",
      "output"          -> "json"
    )

    val mediaType = MediaType.parse(mediaContentType).getOrElse(MediaType.ApplicationOctetStream)

    val request = basicRequest
      .post(url)
      .multipartBody(
        multipart("audio_file", audioBytes)
          .fileName(mediaFilename)
          .contentType(mediaType)
      )
      .readTimeout(1.hour)
      .response(asStringAlways)

    ZIO.scoped {
      for {
        backend  <- HttpClientZioBackend.scopedUsingClient(httpClient)
        response <- request.send(backend)
      } yield response
    }
  }

  private def parseWhisperResponse(
    response: Response[String],
    audioSize: Int,
    mediaContentType: String
  ): Task[WhisperResponse] =
    if (response.code.isSuccess) {
      decode[WhisperResponse](response.body) match {
        case Right(whisperResponse) =>
          if (whisperResponse.text.isEmpty) {
            ZIO.logWarning(
              s"Whisper API returned empty text! Response body: ${response.body.take(500)}"
            ) *> ZIO.succeed(whisperResponse)
          } else {
            ZIO.succeed(whisperResponse)
          }
        case Left(error) =>
          ZIO.fail(
            new RuntimeException(
              s"Invalid JSON from Whisper API (status ${response.code.code}): ${error.getMessage}. Response body: ${response.body.take(500)}"
            )
          )
      }
    } else {
      ZIO.fail(
        new RuntimeException(
          s"Whisper API error (status ${response.code.code}): ${response.body} | Audio: $audioSize bytes, mediaContentType=$mediaContentType"
        )
      )
    }

  private def baseDetails: Map[String, String] = Map(
    "provider" -> "whisper",
    "model"    -> config.modelPath
  )

  override def healthCheck(): Task[HealthStatus] = {
    val serviceName = s"WhisperTranscriber(${config.modelPath})"
    val now         = Instant.now()

    val check = for {
      backend  <- HttpClientZioBackend()
      response <- basicRequest
                    .get(uri"${config.apiUrl}/docs")
                    .readTimeout(10.seconds)
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
            timeoutMs = 10000
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
