package com.cyrelis.srag.application.usecases.ingestion.pipeline.preparator

import java.nio.charset.StandardCharsets
import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.ports.BlobStorePort
import com.cyrelis.srag.domain.ingestionjob.IngestionJob
import com.cyrelis.srag.domain.transcript.{IngestSource, Transcript, Word}
import zio.*

trait TextPreparatorPipeline extends PreparatorPipeline

object TextPreparatorPipeline {
  val layer: ZLayer[BlobStorePort, Nothing, TextPreparatorPipeline] =
    ZLayer {
      for {
        blobStore <- ZIO.service[BlobStorePort]
      } yield new TextPreparatorPipelineLive(blobStore)
    }

  private final class TextPreparatorPipelineLive(
    blobStore: BlobStorePort
  ) extends TextPreparatorPipeline {

    private val textFetchTimeout       = 15.seconds
    private val textFetchRetrySchedule =
      Schedule.exponential(100.millis, 2.0).modifyDelay((_, d) => if (d > 5.seconds) 5.seconds else d) &&
        Schedule.recurs(2)

    override def prepare(job: IngestionJob): ZIO[Any, PipelineError, Transcript] =
      for {
        blobKey <- ZIO
                     .fromOption(job.blobKey)
                     .orElseFail(PipelineError.DatabaseError(s"Missing blob key for text job ${job.id}", None))
        _         <- ZIO.logDebug(s"Job ${job.id} - fetching text blob $blobKey")
        textBytes <- blobStore
                       .fetchAudio(blobKey)
                       .timeoutFail(
                         PipelineError.TimeoutError(
                           operation = s"text_preparation.fetch_blob.${job.id}",
                           timeoutMs = textFetchTimeout.toMillis
                         )
                       )(textFetchTimeout)
                       .retry(textFetchRetrySchedule)
        textContent = new String(textBytes, StandardCharsets.UTF_8)
        _          <- ZIO.logDebug(s"Job ${job.id} - text content loaded: ${textContent.length} chars")
        words       = textContent
                  .split("\\s+")
                  .filter(_.nonEmpty)
                  .zipWithIndex
                  .map { case (wordText, idx) =>
                    Word(
                      text = wordText,
                      start = idx.toLong,
                      end = (idx + 1).toLong,
                      confidence = 1.0
                    )
                  }
                  .toList
        now       <- zio.Clock.instant
        transcript = Transcript(
                       id = UUID.randomUUID(),
                       language = None,
                       words = words,
                       confidence = 1.0,
                       createdAt = now,
                       source = IngestSource.Text,
                       metadata = job.metadata
                     )
        _ <- ZIO.logDebug(s"Job ${job.id} - transcript created: ${transcript.id}")
      } yield transcript
  }
}
