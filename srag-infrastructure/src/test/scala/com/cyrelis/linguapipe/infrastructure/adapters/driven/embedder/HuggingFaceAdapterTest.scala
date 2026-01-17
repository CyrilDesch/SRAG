package com.cyrelis.srag.infrastructure.adapters.driven.embedder

import java.time.Instant
import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.types.HealthStatus
import com.cyrelis.srag.domain.transcript.{IngestSource, Transcript, Word}
import com.cyrelis.srag.infrastructure.adapters.driven.embedder.{HuggingFaceAdapter, HuggingFaceResponse}
import com.cyrelis.srag.infrastructure.config.EmbedderAdapterConfig
import io.circe.syntax.*
import sttp.client4.*
import sttp.model.{Method, RequestMetadata, StatusCode}
import zio.*
import zio.test.*
import zio.test.Assertion.*

object HuggingFaceAdapterTest extends ZIOSpecDefault {

  private def textToWords(text: String): List[Word] =
    if text.isEmpty then List.empty
    else
      text
        .split("\\s+")
        .zipWithIndex
        .map { case (wordText, idx) =>
          Word(
            text = wordText,
            start = idx * 100L,
            end = (idx + 1) * 100L,
            confidence = 0.95
          )
        }
        .toList

  private val testConfig: EmbedderAdapterConfig.HuggingFace = EmbedderAdapterConfig.HuggingFace(
    model = "sentence-transformers/all-MiniLM-L6-v2",
    apiUrl = "http://localhost:8082"
  )

  private val testTranscript: Transcript = Transcript(
    id = UUID.randomUUID(),
    language = None,
    words = textToWords("This is a test transcript for embedding generation."),
    confidence = 0.95,
    createdAt = Instant.now(),
    source = IngestSource.Text,
    metadata = Map.empty
  )

  /** Mock */
  private class HuggingFaceEmbedderAdapterTestDouble(
    config: EmbedderAdapterConfig.HuggingFace,
    stubbedResponse: Task[Response[String]]
  ) extends HuggingFaceAdapter(config) {
    override protected def makeEmbeddingRequest(text: String): Task[Response[String]] =
      stubbedResponse
  }

  def spec = suite("HuggingFaceAdapter")(
    suite("health check scenarios")(
      test("should return Unhealthy with connection error for unreachable server") {
        val invalidConfig: EmbedderAdapterConfig.HuggingFace = EmbedderAdapterConfig.HuggingFace(
          model = "test-model",
          apiUrl = "http://nonexistent-server-xyz:9999"
        )
        val adapter = HuggingFaceAdapter(invalidConfig)

        for {
          status <- adapter.healthCheck()
        } yield assertTrue(
          status match {
            case HealthStatus.Unhealthy(serviceName, _, _, _) =>
              serviceName.contains("HuggingFaceEmbedder")
            case _ => false
          }
        )
      },
      test("should include error details in unhealthy status") {
        val invalidConfig: EmbedderAdapterConfig.HuggingFace = EmbedderAdapterConfig.HuggingFace(
          model = "test-model",
          apiUrl = "http://localhost:99999"
        )
        val adapter = HuggingFaceAdapter(invalidConfig)

        for {
          status <- adapter.healthCheck()
        } yield {
          status match {
            case HealthStatus.Unhealthy(_, _, _, details) =>
              assertTrue(
                details.contains("provider") &&
                  details("provider") == "huggingface" &&
                  details.contains("model") &&
                  details("model") == "test-model"
              )
            case _ => assertTrue(false)
          }
        }
      },
      test("should set service name with model") {
        val adapter = HuggingFaceAdapter(testConfig)

        for {
          status <- adapter.healthCheck()
        } yield {
          val serviceName = status match {
            case HealthStatus.Healthy(name, _, _)      => name
            case HealthStatus.Unhealthy(name, _, _, _) => name
            case HealthStatus.Timeout(name, _, _)      => name
          }
          assertTrue(serviceName.contains("all-MiniLM-L6-v2"))
        }
      }
    ),
    suite("embedding scenarios")(
      test("should successfully embed transcript and return list of segment-embedding pairs") {
        val mockEmbedding = List.fill(384)(0.5f)
        val response      = Response(
          body = HuggingFaceResponse(testTranscript.text, mockEmbedding, 384).asJson.noSpaces,
          code = StatusCode.Ok,
          requestMetadata = RequestMetadata(Method.POST, uri"http://localhost:8082/vectors", Nil)
        )
        val adapter = new HuggingFaceEmbedderAdapterTestDouble(testConfig, ZIO.succeed(response))

        for {
          segments <- adapter.embed(testTranscript)
        } yield assertTrue(
          segments.length == 1 &&
            segments.head._1 == testTranscript.text &&
            segments.head._2.length == 384 &&
            segments.head._2.head == 0.5f
        )
      },
      test("should handle different embedding dimensions") {
        val mockEmbedding = List.fill(768)(0.25f)
        val response      = Response(
          body = HuggingFaceResponse(testTranscript.text, mockEmbedding, 768).asJson.noSpaces,
          code = StatusCode.Ok,
          requestMetadata = RequestMetadata(Method.POST, uri"http://localhost:8082/vectors", Nil)
        )
        val adapter = new HuggingFaceEmbedderAdapterTestDouble(testConfig, ZIO.succeed(response))

        for {
          segments <- adapter.embed(testTranscript)
        } yield assertTrue(
          segments.length == 1 &&
            segments.head._2.length == 768
        )
      },
      test("should propagate embedding errors") {
        val errorMessage = "Embedding service unavailable"
        val adapter      = new HuggingFaceEmbedderAdapterTestDouble(
          testConfig,
          ZIO.fail(new RuntimeException(errorMessage))
        )

        assertZIO(adapter.embed(testTranscript).exit)(
          fails(isSubtype[PipelineError.EmbeddingError](hasField("message", _.message, containsString(errorMessage))))
        )
      },
      test("should handle empty text transcripts") {
        val emptyTranscript = testTranscript.copy(words = List.empty)
        val adapter         = new HuggingFaceAdapter(testConfig)

        for {
          segments <- adapter.embed(emptyTranscript)
        } yield assertTrue(segments.isEmpty)
      },
      test("should chunk long text transcripts into multiple segments") {
        val longText           = ("This is a sentence. " * 100) + "This is another sentence. " * 100
        val longTextTranscript = testTranscript.copy(words = textToWords(longText))

        val mockEmbedding = List.fill(384)(0.1f)
        val adapter       = new HuggingFaceAdapter(testConfig) {
          override protected def makeEmbeddingRequest(text: String): Task[Response[String]] = {
            val response = Response(
              body = HuggingFaceResponse(text, mockEmbedding, 384).asJson.noSpaces,
              code = StatusCode.Ok,
              requestMetadata = RequestMetadata(Method.POST, uri"http://localhost:8082/vectors", Nil)
            )
            ZIO.succeed(response)
          }
        }

        for {
          segments <- adapter.embed(longTextTranscript)
        } yield assertTrue(
          segments.length > 1 &&                // Should have multiple chunks
            segments.forall(_._2.length == 384) // All embeddings have correct dimension
        )
      },
      test("should handle different transcript sources") {
        val audioTranscript = testTranscript.copy(source = IngestSource.Audio)
        val docTranscript   = testTranscript.copy(source = IngestSource.Document)
        val mockEmbedding   = List.fill(384)(0.3f)
        val response        = Response(
          body = HuggingFaceResponse(testTranscript.text, mockEmbedding, 384).asJson.noSpaces,
          code = StatusCode.Ok,
          requestMetadata = RequestMetadata(Method.POST, uri"http://localhost:8082/vectors", Nil)
        )
        val adapter = new HuggingFaceEmbedderAdapterTestDouble(testConfig, ZIO.succeed(response))

        for {
          audioSegments <- adapter.embed(audioTranscript)
          docSegments   <- adapter.embed(docTranscript)
        } yield assertTrue(
          audioSegments.length == 1 &&
            audioSegments.head._2.length == 384 &&
            docSegments.length == 1 &&
            docSegments.head._2.length == 384
        )
      }
    ),
    suite("error handling")(
      test("should handle API error responses") {
        val errorResponse = Response(
          body = """{"error": "Model not found"}""",
          code = StatusCode.NotFound,
          requestMetadata = RequestMetadata(Method.POST, uri"http://localhost:8082/vectors", Nil)
        )
        val adapter = new HuggingFaceEmbedderAdapterTestDouble(
          testConfig,
          ZIO.succeed(errorResponse)
        )

        assertZIO(adapter.embed(testTranscript).exit)(
          fails(isSubtype[PipelineError.EmbeddingError](anything))
        )
      },
      test("should handle malformed JSON responses") {
        val malformedResponse = Response(
          body = "not valid json",
          code = StatusCode.Ok,
          requestMetadata = RequestMetadata(Method.POST, uri"http://localhost:8082/vectors", Nil)
        )
        val adapter = new HuggingFaceEmbedderAdapterTestDouble(
          testConfig,
          ZIO.succeed(malformedResponse)
        )

        assertZIO(adapter.embed(testTranscript).exit)(
          fails(isSubtype[PipelineError.EmbeddingError](anything))
        )
      },
      test("should handle invalid response format") {
        val invalidResponse = Response(
          body = """{"text": "test", "vector": []}""",
          code = StatusCode.Ok,
          requestMetadata = RequestMetadata(Method.POST, uri"http://localhost:8082/vectors", Nil)
        )
        val adapter = new HuggingFaceEmbedderAdapterTestDouble(
          testConfig,
          ZIO.succeed(invalidResponse)
        )

        assertZIO(adapter.embed(testTranscript).exit)(
          fails(isSubtype[PipelineError.EmbeddingError](anything))
        )
      }
    )
  )
}
