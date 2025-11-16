package com.cyrelis.srag.application.pipeline

import java.nio.charset.StandardCharsets
import java.util.UUID

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.ports.driven.storage.BlobStorePort
import com.cyrelis.srag.domain.ingestionjob.IngestionJob
import com.cyrelis.srag.domain.transcript.{IngestSource, Transcript, Word}
import zio.ZIO

final class TextSourcePreparator(
  blobStore: BlobStorePort
) extends SourcePreparator {

  override def prepare(job: IngestionJob): ZIO[Any, PipelineError, Transcript] =
    for {
      blobKey <- ZIO
                   .fromOption(job.blobKey)
                   .orElseFail(PipelineError.DatabaseError(s"Missing blob key for text job ${job.id}", None))
      _          <- ZIO.logDebug(s"Job ${job.id} - fetching text blob $blobKey")
      textBytes  <- blobStore.fetchAudio(blobKey)
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
