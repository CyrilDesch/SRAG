package com.cyrelis.srag.application.usecases.ingestion.pipeline

import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.testsupport.ApplicationSharedSpec
import com.cyrelis.srag.domain.ingestionjob.JobStatus
import com.cyrelis.srag.domain.transcript.IngestSource
import zio.*
import zio.test.*

object IndexingPipelineSpec extends ApplicationSharedSpec {

  override def spec =
    suite("IndexingPipeline")(
      test("index persists transcript, embeds chunks, updates vector and lexical stores") {
        val transcriptId = UUID.randomUUID()
        val transcript   = sampleTranscript(
          id = transcriptId,
          source = IngestSource.Text,
          text = "chunk zero chunk one",
          metadata = Map("tenant" -> "acme")
        )
        val job = sampleJob(
          id = UUID.randomUUID(),
          source = IngestSource.Text,
          status = JobStatus.Embedding,
          attempt = 1,
          blobKey = Some("blob-key"),
          metadata = Map("tenant" -> "acme")
        )
        val segments =
          List(
            ("chunk zero", Array[Float](0.1f, 0.2f)),
            ("chunk one", Array[Float](0.3f, 0.4f))
          )

        for {
          refs <- makeIndexingRefs
          _    <- (for {
                 pipeline <- ZIO.service[IndexingPipeline]
                 _        <- pipeline.index(transcript, job)
               } yield ()).provideLayer(
                 indexingLayer(
                   makeIndexingRepository(refs),
                   makeIndexingEmbedder(refs, _ => ZIO.succeed(segments)),
                   makeIndexingVectorStore(refs),
                   makeIndexingLexicalStore(refs)
                 )
               )
          persisted <- refs.persisted.get
          embedded  <- refs.embedded.get
          upserts   <- refs.upserts.get
          deletions <- refs.deletions.get
          indexed   <- refs.indexedSegments.get
        } yield assertTrue(
          persisted == Vector(transcriptId),
          embedded == Vector(transcriptId),
          upserts == Vector((transcriptId, 2, transcript.metadata)),
          deletions == Vector(transcriptId),
          indexed == Vector((transcriptId, List((0, "chunk zero"), (1, "chunk one")), transcript.metadata))
        )
      },
      test("index keeps indexing lexical segments even when lexical cleanup fails") {
        val transcriptId = UUID.randomUUID()
        val transcript   = sampleTranscript(
          id = transcriptId,
          source = IngestSource.Text,
          text = "alpha beta",
          metadata = Map("team" -> "search")
        )
        val job = sampleJob(
          id = UUID.randomUUID(),
          source = IngestSource.Text,
          status = JobStatus.Embedding,
          attempt = 1,
          blobKey = Some("blob-key")
        )

        for {
          refs <- makeIndexingRefs
          _    <- (for {
                 pipeline <- ZIO.service[IndexingPipeline]
                 _        <- pipeline.index(transcript, job)
               } yield ()).provideLayer(
                 indexingLayer(
                   makeIndexingRepository(refs),
                   makeIndexingEmbedder(refs, _ => ZIO.succeed(List(("alpha beta", Array[Float](0.5f, 0.6f))))),
                   makeIndexingVectorStore(refs),
                   makeIndexingLexicalStore(
                     refs,
                     onDelete = _ => ZIO.fail(PipelineError.LexicalStoreError("purge failed"))
                   )
                 )
               )
          deletions <- refs.deletions.get
          indexed   <- refs.indexedSegments.get
        } yield assertTrue(
          deletions.size == 3,
          deletions.forall(_ == transcriptId),
          indexed == Vector((transcriptId, List((0, "alpha beta")), transcript.metadata))
        )
      }
    ) @@ TestAspect.withLiveClock
}
