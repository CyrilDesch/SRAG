package com.cyrelis.srag.application.ports.driving

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.ports.driven.embedding.EmbedderPort
import com.cyrelis.srag.application.ports.driven.reranker.RerankerPort
import com.cyrelis.srag.application.ports.driven.storage.{LexicalStorePort, VectorStorePort}
import com.cyrelis.srag.application.services.QueryServiceLive
import com.cyrelis.srag.application.types.{ContextSegment, VectorStoreFilter}
import com.cyrelis.srag.domain.transcript.TranscriptRepository
import zio.*

trait QueryPort {
  def retrieveContext(
    queryText: String,
    filter: Option[VectorStoreFilter],
    limit: Int
  ): ZIO[Any, PipelineError, List[ContextSegment]]
}

object QueryPort {

  object TextChunker {
    def chunkText(text: String, chunkSize: Int): List[String] = {
      val words = text.split("\\s+").toList
      words.grouped(chunkSize).map(_.mkString(" ")).toList
    }
  }

  val live: ZLayer[
    EmbedderPort & VectorStorePort & LexicalStorePort & RerankerPort &
      TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]],
    Nothing,
    QueryPort
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
}
