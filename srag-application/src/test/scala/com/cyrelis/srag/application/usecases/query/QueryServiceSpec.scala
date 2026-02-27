package com.cyrelis.srag.application.usecases.query

import java.time.Instant
import java.util.UUID

import com.cyrelis.srag.application.model.query.{LexicalSearchResult, RerankerResult, VectorSearchResult}
import com.cyrelis.srag.application.testsupport.ApplicationSharedSpec
import com.cyrelis.srag.domain.transcript.{IngestSource, Transcript, Word}
import zio.*
import zio.test.*

object QueryServiceSpec extends ApplicationSharedSpec {

  private def rankedVectorResults(transcriptId: UUID, size: Int): List[VectorSearchResult] =
    (0 until size).toList.map { idx =>
      VectorSearchResult(
        transcriptId = transcriptId,
        segmentIndex = idx,
        score = (size - idx).toDouble
      )
    }

  private def rankedLexicalResults(transcriptId: UUID, size: Int): List[LexicalSearchResult] =
    (0 until size).toList.map { idx =>
      LexicalSearchResult(
        transcriptId = transcriptId,
        segmentIndex = idx,
        score = (size - idx).toDouble,
        text = s"text-$idx",
        metadata = Map("segment" -> idx.toString)
      )
    }

  override def spec =
    suite("QueryService")(
      test("returns empty when vector and lexical search both return no candidates") {
        for {
          rerankCalls <- Ref.make(0)
          result      <- (for {
                      service <- ZIO.service[QueryService]
                      value   <- service.retrieveContext("empty query", None, limit = 5)
                    } yield value).provideLayer(
                      queryLayer(
                        makeEmbedder(Array(0.1f, 0.2f)),
                        makeVectorStore(Nil),
                        makeLexicalStore(Nil),
                        makeReranker(rerankCalls, (_, _, _) => ZIO.succeed(Nil)),
                        makeTranscriptRepository(Map.empty)
                      )
                    )
          calls <- rerankCalls.get
        } yield assertTrue(result.isEmpty, calls == 0)
      },
      test("uses reranker output when scores are discriminative") {
        val transcriptId = UUID.randomUUID()
        val vector       = rankedVectorResults(transcriptId, size = 5)
        val lexical      = rankedLexicalResults(transcriptId, size = 5)
        val transcript   =
          Transcript(
            id = transcriptId,
            language = None,
            words = List(Word("fallback", 0L, 1L, 1.0)),
            confidence = 1.0,
            createdAt = Instant.now(),
            source = IngestSource.Text,
            metadata = Map.empty
          )

        val rerankScores = Map(
          4 -> 0.91,
          3 -> 0.85,
          2 -> 0.40,
          1 -> 0.35,
          0 -> 0.20
        )

        for {
          rerankCalls <- Ref.make(0)
          result      <- (for {
                      service <- ZIO.service[QueryService]
                      value   <- service.retrieveContext("top answer", None, limit = 5)
                    } yield value).provideLayer(
                      queryLayer(
                        makeEmbedder(Array(0.9f, 0.1f)),
                        makeVectorStore(vector),
                        makeLexicalStore(lexical),
                        makeReranker(
                          rerankCalls,
                          (_, candidates, _) =>
                            ZIO.succeed(
                              candidates.flatMap { candidate =>
                                rerankScores.get(candidate.segmentIndex).map(score => RerankerResult(candidate, score))
                              }
                            )
                        ),
                        makeTranscriptRepository(Map(transcriptId -> transcript))
                      )
                    )
          calls <- rerankCalls.get
        } yield assertTrue(
          calls == 1,
          result.map(_.segmentIndex) == List(4, 3),
          result.map(_.score) == List(0.91, 0.85),
          result.map(_.text) == List("text-4", "text-3")
        )
      }
    ) @@ TestAspect.withLiveClock
}
