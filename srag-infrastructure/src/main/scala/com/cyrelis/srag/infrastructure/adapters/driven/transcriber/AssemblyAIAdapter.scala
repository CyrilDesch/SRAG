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
import io.circe.syntax.*
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.MediaType
import zio.*

final case class AssemblyAIUploadResponse(upload_url: String) derives Codec

final case class AssemblyAITranscriptSubmitRequest(
  audio_url: String,
  language_detection: Boolean
) derives Codec

final case class AssemblyAITranscriptSubmitResponse(
  id: String,
  status: String,
  audio_url: String,
  language_code: Option[String],
  language_confidence: Option[Double],
  text: Option[String],
  error: Option[String]
) derives Codec

final case class AssemblyAITranscriptStatusResponse(
  id: String,
  status: String,
  audio_url: String,
  language_code: Option[String],
  language_confidence: Option[Double],
  text: Option[String],
  confidence: Option[Double],
  error: Option[String],
  words: Option[List[AssemblyAIWord]] = None
) derives Codec

final case class AssemblyAIWord(
  text: String,
  start: Long,
  end: Long,
  confidence: Double,
  speaker: Option[String] = None
) derives Codec

class AssemblyAIAdapter(config: TranscriberAdapterConfig.AssemblyAI) extends TranscriberPort {

  private val httpClient: HttpClient =
    HttpClient
      .newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .build()

  private val baseUrl = s"https://${config.apiUrl}"
  private val apiKey  = config.apiKey

  override def transcribe(
    audioContent: Array[Byte],
    mediaContentType: String,
    mediaFilename: String
  ): ZIO[Any, com.cyrelis.srag.application.errors.PipelineError, Transcript] = {
    val transcriptId = UUID.randomUUID()
    val now          = Instant.now()

    ErrorMapper.mapTranscriptionError {
      for {
        uploadUrl              <- uploadAudioFile(audioContent, mediaContentType, mediaFilename)
        _                      <- ZIO.logDebug(s"Audio file uploaded to AssemblyAI: $uploadUrl")
        assemblyAITranscriptId <- submitTranscript(uploadUrl)
        _                      <- ZIO.logDebug(s"Transcript submitted to AssemblyAI: $assemblyAITranscriptId")
        transcriptResponse     <- pollTranscriptStatus(assemblyAITranscriptId)
        _                      <- ZIO.logDebug(s"Transcript completed: ${transcriptResponse.id}")
        transcript             <- buildTranscript(transcriptId, transcriptResponse, now)
      } yield transcript
    }
  }

  private def uploadAudioFile(
    audioBytes: Array[Byte],
    _mediaContentType: String,
    _mediaFilename: String
  ): Task[String] = {
    val url = uri"$baseUrl/v2/upload"

    ZIO.logDebug(
      s"Uploading audio to AssemblyAI: size=${audioBytes.length} bytes, filename=${_mediaFilename}, contentType=${_mediaContentType}"
    ) *> {
      val request = basicRequest
        .post(url)
        .header("Authorization", apiKey)
        .body(audioBytes)
        .contentType(MediaType.ApplicationOctetStream)
        .readTimeout(5.minutes)
        .response(asStringAlways)

      ZIO.scoped {
        for {
          backend        <- HttpClientZioBackend.scopedUsingClient(httpClient)
          response       <- request.send(backend)
          _              <- ZIO.logDebug(s"Upload response: status=${response.code.code}, body=${response.body.take(200)}")
          uploadResponse <- parseUploadResponse(response)
          _              <- ZIO.logDebug(s"Upload successful, got URL: ${uploadResponse.upload_url}")
        } yield uploadResponse.upload_url
      }
    }
  }

  private def parseUploadResponse(response: Response[String]): Task[AssemblyAIUploadResponse] =
    if (response.code.isSuccess) {
      decode[AssemblyAIUploadResponse](response.body) match {
        case Right(uploadResponse) => ZIO.succeed(uploadResponse)
        case Left(error)           =>
          ZIO.fail(
            new RuntimeException(
              s"Invalid JSON from AssemblyAI upload API (status ${response.code.code}): ${error.getMessage}. Response body: ${response.body.take(500)}"
            )
          )
      }
    } else {
      ZIO.fail(
        new RuntimeException(
          s"AssemblyAI upload API error (status ${response.code.code}): ${response.body}"
        )
      )
    }

  private def submitTranscript(audioUrl: String): Task[String] = {
    val url = uri"$baseUrl/v2/transcript"

    val requestBody = AssemblyAITranscriptSubmitRequest(audio_url = audioUrl, language_detection = true)

    val request = basicRequest
      .post(url)
      .header("Authorization", apiKey)
      .header("Content-Type", "application/json")
      .body(requestBody.asJson.noSpaces)
      .readTimeout(30.seconds)
      .response(asStringAlways)

    ZIO.scoped {
      for {
        backend        <- HttpClientZioBackend.scopedUsingClient(httpClient)
        response       <- request.send(backend)
        _              <- ZIO.logDebug(s"Submit transcript response: status=${response.code.code}")
        submitResponse <- parseSubmitResponse(response)
      } yield submitResponse.id
    }
  }

  private def parseSubmitResponse(response: Response[String]): Task[AssemblyAITranscriptSubmitResponse] =
    if (response.code.isSuccess) {
      decode[AssemblyAITranscriptSubmitResponse](response.body) match {
        case Right(submitResponse) =>
          submitResponse.error match {
            case Some(error) =>
              ZIO.fail(new RuntimeException(s"AssemblyAI transcript submission error: $error"))
            case None => ZIO.succeed(submitResponse)
          }
        case Left(error) =>
          ZIO.fail(
            new RuntimeException(
              s"Invalid JSON from AssemblyAI submit API (status ${response.code.code}): ${error.getMessage}. Response body: ${response.body.take(500)}"
            )
          )
      }
    } else {
      ZIO.fail(
        new RuntimeException(
          s"AssemblyAI submit API error (status ${response.code.code}): ${response.body}"
        )
      )
    }

  private def pollTranscriptStatus(transcriptId: String): Task[AssemblyAITranscriptStatusResponse] = {
    val url = uri"$baseUrl/v2/transcript/$transcriptId"

    def pollOnce: Task[AssemblyAITranscriptStatusResponse] = {
      val request = basicRequest
        .get(url)
        .header("Authorization", apiKey)
        .readTimeout(30.seconds)
        .response(asStringAlways)

      ZIO.scoped {
        for {
          backend        <- HttpClientZioBackend.scopedUsingClient(httpClient)
          response       <- request.send(backend)
          statusResponse <- parseStatusResponse(response)
        } yield statusResponse
      }
    }

    def pollUntilComplete: Task[AssemblyAITranscriptStatusResponse] =
      pollOnce.flatMap { response =>
        ZIO.logDebug(
          s"AssemblyAI poll response: status=${response.status}, text present=${response.text.isDefined}, text length=${response.text.map(_.length).getOrElse(0)}, words count=${response.words.map(_.length).getOrElse(0)}"
        ) *> {
          response.status match {
            case "completed" =>
              response.error match {
                case Some(error) =>
                  ZIO.fail(new RuntimeException(s"AssemblyAI transcript error: $error"))
                case None =>
                  // If text is empty, wait a bit and poll again - sometimes AssemblyAI needs a moment to finalize
                  if (response.text.isEmpty || response.text.exists(_.isEmpty)) {
                    ZIO.logWarning(
                      s"AssemblyAI transcript completed but text is empty. Waiting 1 second and polling again..."
                    ) *> ZIO.sleep(1.second) *> pollOnce.flatMap { retryResponse =>
                      if (retryResponse.text.exists(_.nonEmpty)) {
                        ZIO.logInfo(s"AssemblyAI text now available after retry")
                        ZIO.succeed(retryResponse)
                      } else {
                        ZIO.logWarning(
                          s"AssemblyAI transcript still empty after retry. Response: id=${retryResponse.id}, text=${retryResponse.text}, words=${retryResponse.words.map(_.length).getOrElse(0)}"
                        )
                        ZIO.succeed(retryResponse)
                      }
                    }
                  } else {
                    ZIO.succeed(response)
                  }
              }
            case "error" =>
              ZIO.fail(
                new RuntimeException(
                  s"AssemblyAI transcript failed: ${response.error.getOrElse("Unknown error")}"
                )
              )
            case "queued" | "processing" =>
              ZIO.sleep(2.seconds) *> pollUntilComplete
            case other =>
              ZIO.fail(
                new RuntimeException(s"Unknown AssemblyAI transcript status: $other")
              )
          }
        }
      }

    pollUntilComplete
  }

  private def parseStatusResponse(
    response: Response[String]
  ): Task[AssemblyAITranscriptStatusResponse] =
    if (response.code.isSuccess) {
      decode[AssemblyAITranscriptStatusResponse](response.body) match {
        case Right(statusResponse) =>
          ZIO.logDebug(
            s"AssemblyAI status response: id=${statusResponse.id}, status=${statusResponse.status}, text present=${statusResponse.text.isDefined}, text length=${statusResponse.text.map(_.length).getOrElse(0)}, words count=${statusResponse.words.map(_.length).getOrElse(0)}"
          ) *> ZIO.succeed(statusResponse)
        case Left(error) =>
          ZIO.logError(
            s"Failed to parse AssemblyAI response: ${error.getMessage}. Response body: ${response.body.take(1000)}"
          ) *> ZIO.fail(
            new RuntimeException(
              s"Invalid JSON from AssemblyAI status API (status ${response.code.code}): ${error.getMessage}. Response body: ${response.body.take(500)}"
            )
          )
      }
    } else {
      ZIO.fail(
        new RuntimeException(
          s"AssemblyAI status API error (status ${response.code.code}): ${response.body}"
        )
      )
    }

  private def buildTranscript(
    transcriptId: UUID,
    response: AssemblyAITranscriptStatusResponse,
    now: Instant
  ): Task[Transcript] =
    for {
      confidence <- ZIO
                      .fromOption(response.confidence)
                      .orElseFail(
                        new RuntimeException(
                          s"AssemblyAI transcript completed but confidence is missing. Transcript ID: ${response.id}"
                        )
                      )
      words <- ZIO
                 .fromOption(
                   response.words
                     .filter(_.nonEmpty)
                     .map(_.map(w => Word(w.text, w.start, w.end, w.confidence)))
                     .orElse {
                       response.text.collect {
                         case t if t.nonEmpty =>
                           List(Word(t, 0L, 0L, confidence))
                       }
                     }
                 )
                 .orElseFail(
                   new RuntimeException(
                     s"AssemblyAI transcript completed but contains no words or text. Transcript ID: ${response.id}"
                   )
                 )
      language = response.language_code.flatMap { code =>
                   // AssemblyAI returns codes like "en_us", "fr", etc. Convert to ISO 639-1
                   val isoCode = code.split("_").head.toLowerCase
                   LanguageCode.fromString(isoCode)
                 }
      _ <-
        ZIO.logDebug(
          s"Building transcript: id=$transcriptId, words count=${words.length}, language=$language, confidence=$confidence"
        )
    } yield Transcript(
      id = transcriptId,
      language = language,
      words = words,
      confidence = confidence,
      createdAt = now,
      source = IngestSource.Audio,
      metadata = Map(
        "provider"            -> "assemblyai",
        "assemblyai_id"       -> response.id,
        "language_confidence" -> response.language_confidence.map(_.toString).getOrElse("unknown")
      )
    )

  override def healthCheck(): Task[HealthStatus] = {
    val serviceName = s"AssemblyAITranscriber(${config.apiUrl})"
    val now         = Instant.now()

    val check = for {
      backend  <- HttpClientZioBackend()
      response <- basicRequest
                    .get(uri"$baseUrl/v2/transcript")
                    .header("Authorization", apiKey)
                    .readTimeout(10.seconds)
                    .send(backend)
      _ <- backend.close()
    } yield {
      if (response.code.code >= 200 && response.code.code < 500) {
        HealthStatus.Healthy(
          serviceName = serviceName,
          checkedAt = now,
          details = Map("provider" -> "assemblyai", "status" -> "connected")
        )
      } else {
        HealthStatus.Unhealthy(
          serviceName = serviceName,
          checkedAt = now,
          error = s"HTTP ${response.code.code}",
          details = Map("provider" -> "assemblyai", "response_code" -> response.code.code.toString)
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
            details = Map("provider" -> "assemblyai", "error_type" -> "connection")
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
            details = Map("provider" -> "assemblyai", "error_type" -> "unexpected")
          )
      })
    }
  }
}
