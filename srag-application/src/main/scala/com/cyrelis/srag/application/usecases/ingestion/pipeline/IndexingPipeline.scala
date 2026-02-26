package com.cyrelis.srag.application.usecases.ingestion.pipeline

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.ports.{EmbedderPort, LexicalStorePort, VectorStorePort}
import com.cyrelis.srag.domain.ingestionjob.IngestionJob
import com.cyrelis.srag.domain.transcript.{Transcript, TranscriptRepository}
import zio.*

trait IndexingPipeline {
  def index(transcript: Transcript, job: IngestionJob): ZIO[Any, PipelineError, Unit]
}

object IndexingPipeline {
  val layer: ZLayer[
    TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]] & EmbedderPort & VectorStorePort & LexicalStorePort,
    Nothing,
    IndexingPipeline
  ] =
    ZLayer {
      for {
        transcriptRepository <- ZIO.service[TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]]]
        embedder             <- ZIO.service[EmbedderPort]
        vectorSink           <- ZIO.service[VectorStorePort]
        lexicalStore         <- ZIO.service[LexicalStorePort]
      } yield new IndexingPipelineLive(transcriptRepository, embedder, vectorSink, lexicalStore)
    }

  private final class IndexingPipelineLive(
    transcriptRepository: TranscriptRepository[[X] =>> ZIO[Any, PipelineError, X]],
    embedder: EmbedderPort,
    vectorSink: VectorStorePort,
    lexicalStore: LexicalStorePort
  ) extends IndexingPipeline {

    override def index(transcript: Transcript, job: IngestionJob): ZIO[Any, PipelineError, Unit] =
      for {
        _                  <- ZIO.logDebug(s"Job ${job.id} - persisting transcript ${transcript.id}")
        _                  <- transcriptRepository.persist(transcript)
        _                  <- ZIO.logDebug(s"Job ${job.id} - generating embeddings")
        segments           <- embedder.embed(transcript)
        _                  <- ZIO.logDebug(s"Job ${job.id} - embeddings generated: ${segments.size} chunks")
        chunkVectors        = segments.map(_._2)
        chunkTextsWithIndex = segments.zipWithIndex.map { case ((text, _), index) => (index, text) }
        _                  <- ZIO.logDebug(s"Job ${job.id} - upserting ${chunkVectors.size} vectors into vector store")
        _                  <- vectorSink.upsertEmbeddings(transcript.id, chunkVectors, transcript.metadata)
        _                  <- ZIO.logDebug(s"Job ${job.id} - vectors upserted successfully")
        _                  <- ZIO.logDebug(s"Job ${job.id} - purging old lexical index")
        _                  <-
          lexicalStore
            .deleteTranscript(transcript.id)
            .catchAll(error => ZIO.logWarning(s"Failed to purge lexical index for ${transcript.id}: ${error.message}"))
        _ <- ZIO.logDebug(s"Job ${job.id} - indexing ${chunkTextsWithIndex.size} segments into lexical store")
        _ <- lexicalStore.indexSegments(transcript.id, chunkTextsWithIndex, transcript.metadata)
        _ <- ZIO.logDebug(s"Job ${job.id} - lexical index updated successfully")
      } yield ()
  }
}
