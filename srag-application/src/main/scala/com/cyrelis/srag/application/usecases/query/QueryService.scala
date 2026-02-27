package com.cyrelis.srag.application.usecases.query

import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.model.query.{
  ContextSegment,
  LexicalSearchResult,
  RerankerCandidate,
  RerankerResult,
  VectorSearchResult,
  VectorStoreFilter
}
import com.cyrelis.srag.application.ports.{EmbedderPort, LexicalStorePort, RerankerPort, VectorStorePort}
import com.cyrelis.srag.domain.transcript.TranscriptRepository
import zio.*

trait QueryService {
  def retrieveContext(
    queryText: String,
    filter: Option[VectorStoreFilter],
    limit: Int
  ): ZIO[Any, PipelineError, List[ContextSegment]]
}

object QueryService {

  object TextChunker {
    def chunkText(text: String, chunkSize: Int): List[String] = {
      val words = text.split("\\s+").toList
      words.grouped(chunkSize).map(_.mkString(" ")).toList
    }
  }

  val layer: ZLayer[
    EmbedderPort & VectorStorePort & LexicalStorePort & RerankerPort &
      TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]],
    Nothing,
    QueryService
  ] =
    ZLayer {
      for {
        embedder             <- ZIO.service[EmbedderPort]
        vectorStore          <- ZIO.service[VectorStorePort]
        lexicalStore         <- ZIO.service[LexicalStorePort]
        reranker             <- ZIO.service[RerankerPort]
        transcriptRepository <- ZIO.service[TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]]]
      } yield new QueryServiceLive(
        embedder = embedder,
        vectorStore = vectorStore,
        lexicalStore = lexicalStore,
        reranker = reranker,
        transcriptRepository = transcriptRepository
      )
    }

  private final class QueryServiceLive(
    embedder: EmbedderPort,
    vectorStore: VectorStorePort,
    lexicalStore: LexicalStorePort,
    reranker: RerankerPort,
    transcriptRepository: TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]]
  ) extends QueryService {

    private val fusionPoolSize         = 200
    private val rerankerPoolSize       = 200
    private val rrfK                   = 60
    private val minCandidatesForRerank = 5   // Minimum candidates needed to use reranking
    private val rerankerTopKRatio      = 0.2 // Keep results within 20% of top reranker score
    private val minAcceptableGap       = 0.5 // Minimum score difference to consider results discriminated
    private val minAbsoluteScore       = 0.3 // Minimum absolute score for top result (0.3 on 0-1 scale)

    private val embeddingTimeout    = 30.seconds
    private val vectorStoreTimeout  = 10.seconds
    private val lexicalStoreTimeout = 10.seconds
    private val rerankerTimeout     = 15.seconds
    private val databaseTimeout     = 5.seconds

    private val embeddingRetrySchedule =
      Schedule.exponential(100.millis, 2.0).modifyDelay((_, d) => if (d > 5.seconds) 5.seconds else d) &&
        Schedule.recurs(2)
    private val vectorStoreRetrySchedule =
      Schedule.exponential(100.millis, 2.0).modifyDelay((_, d) => if (d > 5.seconds) 5.seconds else d) &&
        Schedule.recurs(2)
    private val lexicalStoreRetrySchedule =
      Schedule.exponential(100.millis, 2.0).modifyDelay((_, d) => if (d > 5.seconds) 5.seconds else d) &&
        Schedule.recurs(2)
    private val rerankerRetrySchedule =
      Schedule.exponential(100.millis, 2.0).modifyDelay((_, d) => if (d > 5.seconds) 5.seconds else d) &&
        Schedule.recurs(1)
    private val databaseRetrySchedule =
      Schedule.exponential(100.millis, 2.0).modifyDelay((_, d) => if (d > 5.seconds) 5.seconds else d) &&
        Schedule.recurs(2)

    override def retrieveContext(
      queryText: String,
      filter: Option[VectorStoreFilter],
      limit: Int = 5
    ): ZIO[Any, PipelineError, List[ContextSegment]] =
      for {
        queryVector <- embedder
                         .embedQuery(queryText)
                         .timeoutFail(
                           PipelineError.TimeoutError(
                             operation = "query.embed_query",
                             timeoutMs = embeddingTimeout.toMillis
                           )
                         )(embeddingTimeout)
                         .retry(embeddingRetrySchedule)
        semanticResults <- vectorStore
                             .searchSimilar(queryVector, fusionPoolSize, filter)
                             .timeoutFail(
                               PipelineError.TimeoutError(
                                 operation = "query.vector_search",
                                 timeoutMs = vectorStoreTimeout.toMillis
                               )
                             )(vectorStoreTimeout)
                             .retry(vectorStoreRetrySchedule)
        lexicalResults <- lexicalStore
                            .search(queryText, fusionPoolSize, filter)
                            .timeoutFail(
                              PipelineError.TimeoutError(
                                operation = "query.lexical_search",
                                timeoutMs = lexicalStoreTimeout.toMillis
                              )
                            )(lexicalStoreTimeout)
                            .retry(lexicalStoreRetrySchedule)

        fusedCandidates = fuseCandidates(semanticResults, lexicalResults)

        result <- if (fusedCandidates.isEmpty) ZIO.succeed(List.empty[ContextSegment])
                  else buildContextSegments(queryText, fusedCandidates, lexicalResults, limit)
      } yield result

    private type CandidateKey = (UUID, Int)

    private def fuseCandidates(
      semantic: List[VectorSearchResult],
      lexical: List[LexicalSearchResult]
    ): List[(CandidateKey, Double)] = {
      val semanticRanked = semantic
        .sortBy(result => -result.score)
        .zipWithIndex
        .map { case (result, idx) =>
          ((result.transcriptId, result.segmentIndex), idx + 1)
        }
        .toMap

      val lexicalRanked = lexical
        .sortBy(result => -result.score)
        .zipWithIndex
        .map { case (result, idx) =>
          ((result.transcriptId, result.segmentIndex), idx + 1)
        }
        .toMap

      val allKeys = semanticRanked.keySet ++ lexicalRanked.keySet

      allKeys.toList.map { key =>
        val semanticScore = semanticRanked.get(key).map(rank => 1.0 / (rrfK + rank)).getOrElse(0.0)
        val lexicalScore  = lexicalRanked.get(key).map(rank => 1.0 / (rrfK + rank)).getOrElse(0.0)
        key -> (semanticScore + lexicalScore)
      }
        .filter(_._2 > 0.0)
        .sortBy { case (_, score) => -score }
        .take(fusionPoolSize)
    }

    private def buildContextSegments(
      queryText: String,
      fused: List[(CandidateKey, Double)],
      lexicalResults: List[LexicalSearchResult],
      limit: Int
    ): ZIO[Any, PipelineError, List[ContextSegment]] = {
      val transcriptIds = fused.map(_._1._1).distinct

      for {
        transcripts <- ZIO
                         .foreach(transcriptIds)(id =>
                           transcriptRepository
                             .getById(id)
                             .timeoutFail(
                               PipelineError.TimeoutError(
                                 operation = s"query.fetch_transcript.$id",
                                 timeoutMs = databaseTimeout.toMillis
                               )
                             )(databaseTimeout)
                             .retry(databaseRetrySchedule)
                             .map(_.map(id -> _))
                         )
        transcriptMap        = transcripts.collect { case Some(value) => value }.toMap
        chunkIndex           = buildChunkIndex(transcriptMap.values.toList)
        lexicalIndex         = lexicalResults.map(result => ((result.transcriptId, result.segmentIndex), result.text)).toMap
        candidatesWithScores = fused.flatMap { case (key, fusedScore) =>
                                 val text = lexicalIndex.get(key).filter(_.nonEmpty).orElse(chunkIndex.get(key))
                                 text.map { chunkText =>
                                   (RerankerCandidate(key._1, key._2, chunkText), fusedScore)
                                 }
                               }
        fusionResults = candidatesWithScores.sortBy { case (_, fusedScore) => -fusedScore }
                          .take(limit)
                          .map { case (candidate, fusedScore) =>
                            ContextSegment(
                              transcriptId = candidate.transcriptId,
                              segmentIndex = candidate.segmentIndex,
                              text = candidate.text,
                              score = fusedScore
                            )
                          }
        finalResult <- if (candidatesWithScores.size < minCandidatesForRerank) {
                         ZIO.succeed(fusionResults)
                       } else {
                         val candidatesOnly = candidatesWithScores.map(_._1)
                         val rerankTopK     = math.min(rerankerPoolSize, candidatesOnly.size)
                         reranker
                           .rerank(queryText, candidatesOnly, rerankTopK)
                           .timeoutFail(
                             PipelineError.TimeoutError(
                               operation = "query.rerank",
                               timeoutMs = rerankerTimeout.toMillis
                             )
                           )(rerankerTimeout)
                           .retry(rerankerRetrySchedule)
                           .either
                           .flatMap {
                             case Right(reranked) if reranked.nonEmpty =>
                               filterRerankedResults(reranked, limit)
                             case Right(_) =>
                               ZIO.succeed(fusionResults)
                             case Left(error) =>
                               ZIO.logWarning(s"Reranker failed: ${error.message}, using fusion scores") *>
                                 ZIO.succeed(fusionResults)
                           }
                       }
      } yield finalResult
    }

    private def filterRerankedResults(
      reranked: List[RerankerResult],
      limit: Int
    ): ZIO[Any, PipelineError, List[ContextSegment]] =
      if (reranked.isEmpty) {
        ZIO.succeed(List.empty)
      } else {
        val sorted     = reranked.sortBy(res => -res.score)
        val scores     = sorted.map(_.score)
        val topScore   = scores.head
        val worstScore = scores.last
        val gap        = topScore - worstScore

        if (topScore < minAbsoluteScore || gap < minAcceptableGap) {
          ZIO.succeed(List.empty)
        } else {
          val threshold = topScore - (gap * rerankerTopKRatio)
          val filtered  = sorted
            .filter(_.score >= threshold)
            .take(limit)
            .map(toContextSegment)

          if (filtered.isEmpty) {
            ZIO.succeed(List.empty)
          } else {
            ZIO.succeed(filtered)
          }
        }
      }

    private def buildChunkIndex(
      transcripts: List[com.cyrelis.srag.domain.transcript.Transcript]
    ): Map[CandidateKey, String] =
      transcripts.flatMap { transcript =>
        val chunks = TextChunker.chunkText(transcript.text, 1000)
        chunks.zipWithIndex.map { case (chunk, idx) => (transcript.id -> idx) -> chunk }
      }.toMap

    private def toContextSegment(result: RerankerResult): ContextSegment =
      ContextSegment(
        transcriptId = result.candidate.transcriptId,
        segmentIndex = result.candidate.segmentIndex,
        text = result.candidate.text,
        score = result.score
      )
  }
}
