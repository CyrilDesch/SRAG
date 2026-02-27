package com.cyrelis.srag.domain.transcript

import java.time.Instant
import java.util.UUID

import zio.test.*
import zio.test.Assertion.*

object TranscriptSpec extends ZIOSpecDefault:

  override def spec = suite("Transcript")(
    test("text concatenates words correctly") {
      val transcript = Transcript(
        id = UUID.randomUUID(),
        language = LanguageCode.fromString("en"),
        words = List(
          Word("Hello,", 0L, 500L, 0.99),
          Word("world!", 600L, 1000L, 0.95)
        ),
        confidence = 0.97,
        createdAt = Instant.parse("2026-02-27T10:00:00Z"),
        source = IngestSource.Audio,
        metadata = Map.empty
      )

      assert(transcript.text)(equalTo("Hello, world!"))
    },
    test("addMetadata adds single key properly") {
      val transcript = Transcript(
        id = UUID.randomUUID(),
        language = LanguageCode.fromString("en"),
        words = List(),
        confidence = 1.0,
        createdAt = Instant.parse("2026-02-27T10:00:00Z"),
        source = IngestSource.Audio,
        metadata = Map("existing" -> "value")
      )

      val updated = transcript.addMetadata("newKey", "newValue")

      assert(updated.metadata)(equalTo(Map("existing" -> "value", "newKey" -> "newValue")))
    }
  )
