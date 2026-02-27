package com.cyrelis.srag.application.testsupport

import java.time.Instant
import java.util.UUID

import scala.concurrent.duration.*

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.model.healthcheck.HealthStatus
import com.cyrelis.srag.application.model.ingestion.JobProcessingConfig
import com.cyrelis.srag.application.model.query.{
  LexicalSearchResult,
  RerankerCandidate,
  RerankerResult,
  VectorSearchResult,
  VectorStoreFilter
}
import com.cyrelis.srag.application.ports.{
  BlobInfo,
  BlobStorePort,
  DatasourcePort,
  DocumentInfo,
  EmbedderPort,
  JobQueuePort,
  LexicalStorePort,
  RerankerPort,
  TranscriberPort,
  VectorInfo,
  VectorStorePort
}
import com.cyrelis.srag.application.testsupport.SpecSupport.{AppTask, unexpectedPipelineCall, unexpectedTaskCall}
import com.cyrelis.srag.application.usecases.healthcheck.HealthCheckService
import com.cyrelis.srag.application.usecases.ingestion.pipeline.IndexingPipeline
import com.cyrelis.srag.application.usecases.ingestion.pipeline.preparator.{
  AudioPreparatorPipeline,
  PreparatorRouterPipeline,
  TextPreparatorPipeline
}
import com.cyrelis.srag.application.usecases.ingestion.{IngestService, IngestionWorker}
import com.cyrelis.srag.application.usecases.query.QueryService
import com.cyrelis.srag.domain.ingestionjob.{IngestionJob, IngestionJobRepository, JobStatus}
import com.cyrelis.srag.domain.transcript.{IngestSource, Transcript, TranscriptRepository, Word}
import zio.*
import zio.stream.ZStream
import zio.test.ZIOSpec

object ApplicationSharedLayers {
  val sharedJobProcessingConfig: JobProcessingConfig =
    JobProcessingConfig(
      maxAttempts = 3,
      pollInterval = 100.millis,
      batchSize = 10,
      initialRetryDelay = 100.millis,
      maxRetryDelay = 5.seconds,
      backoffFactor = 2.0,
      maxConcurrentJobs = 1
    )

  val bootstrap: ULayer[JobProcessingConfig] =
    ZLayer.succeed(sharedJobProcessingConfig)
}

final case class IngestRefs(
  storedText: Ref[Vector[(UUID, String)]],
  storedAudio: Ref[Vector[(UUID, String, String)]],
  createdJobs: Ref[Vector[IngestionJob]],
  enqueued: Ref[Vector[UUID]]
)

final case class IndexingRefs(
  persisted: Ref[Vector[UUID]],
  embedded: Ref[Vector[UUID]],
  upserts: Ref[Vector[(UUID, Int, Map[String, String])]],
  deletions: Ref[Vector[UUID]],
  indexedSegments: Ref[Vector[(UUID, List[(Int, String)], Map[String, String])]]
)

final case class AudioPreparatorRefs(
  fetchedBlobKeys: Ref[Vector[String]],
  transcribeCalls: Ref[Vector[(List[Byte], String, String)]]
)

final case class TextPreparatorRefs(
  fetchedBlobKeys: Ref[Vector[String]]
)

final case class RouterRefs(
  audioCalls: Ref[Vector[UUID]],
  textCalls: Ref[Vector[UUID]]
)

final case class WorkerRefs(
  jobs: Ref[Map[UUID, IngestionJob]],
  updates: Ref[Vector[IngestionJob]],
  preparedJobs: Ref[Vector[UUID]],
  indexedJobs: Ref[Vector[UUID]],
  deletedBlobs: Ref[Vector[String]],
  claimCalls: Ref[Vector[Int]],
  heartbeatCalls: Ref[Vector[UUID]],
  acked: Ref[Vector[UUID]],
  released: Ref[Vector[UUID]],
  enqueued: Ref[Vector[UUID]],
  deadLettered: Ref[Vector[(UUID, String)]]
)

trait SharedPortMakers {
  protected def makeEmbedder(queryVector: Array[Float]): EmbedderPort =
    new EmbedderPort {
      override def embed(transcript: Transcript): AppTask[List[(String, Array[Float])]] =
        unexpectedPipelineCall(s"EmbedderPort.embed(${transcript.id})")

      override def embedQuery(query: String): AppTask[Array[Float]] =
        ZIO.succeed(queryVector)

      override def healthCheck() = unexpectedTaskCall("EmbedderPort.healthCheck")
    }

  protected def makeVectorStore(results: List[VectorSearchResult]): VectorStorePort =
    new VectorStorePort {
      override def upsertEmbeddings(
        transcriptId: UUID,
        vectors: List[Array[Float]],
        metadata: Map[String, String]
      ): AppTask[Unit] =
        unexpectedPipelineCall(s"VectorStorePort.upsertEmbeddings($transcriptId)")

      override def searchSimilar(
        queryVector: Array[Float],
        limit: Int,
        filter: Option[VectorStoreFilter]
      ): AppTask[List[VectorSearchResult]] =
        ZIO.succeed(results.take(limit))

      override def listAllVectors(): AppTask[List[VectorInfo]] =
        unexpectedPipelineCall("VectorStorePort.listAllVectors")

      override def healthCheck() = unexpectedTaskCall("VectorStorePort.healthCheck")
    }

  protected def makeLexicalStore(results: List[LexicalSearchResult]): LexicalStorePort =
    new LexicalStorePort {
      override def indexSegments(
        transcriptId: UUID,
        segments: List[(Int, String)],
        metadata: Map[String, String]
      ): AppTask[Unit] =
        unexpectedPipelineCall(s"LexicalStorePort.indexSegments($transcriptId)")

      override def deleteTranscript(transcriptId: UUID): AppTask[Unit] =
        unexpectedPipelineCall(s"LexicalStorePort.deleteTranscript($transcriptId)")

      override def search(
        queryText: String,
        limit: Int,
        filter: Option[VectorStoreFilter]
      ): AppTask[List[LexicalSearchResult]] =
        ZIO.succeed(results.take(limit))

      override def listAllDocuments(): AppTask[List[DocumentInfo]] =
        unexpectedPipelineCall("LexicalStorePort.listAllDocuments")

      override def healthCheck() = unexpectedTaskCall("LexicalStorePort.healthCheck")
    }

  protected def makeReranker(
    rerankCalls: Ref[Int],
    rerankResponse: (String, List[RerankerCandidate], Int) => AppTask[List[RerankerResult]]
  ): RerankerPort =
    new RerankerPort {
      override def rerank(
        query: String,
        candidates: List[RerankerCandidate],
        topK: Int
      ): AppTask[List[RerankerResult]] =
        rerankCalls.update(_ + 1) *> rerankResponse(query, candidates, topK)

      override def healthCheck() = unexpectedTaskCall("RerankerPort.healthCheck")
    }
}

trait SharedIngestionMakers {
  protected def makeRefs: UIO[IngestRefs] =
    for {
      storedText  <- Ref.make(Vector.empty[(UUID, String)])
      storedAudio <- Ref.make(Vector.empty[(UUID, String, String)])
      createdJobs <- Ref.make(Vector.empty[IngestionJob])
      enqueued    <- Ref.make(Vector.empty[UUID])
    } yield IngestRefs(storedText, storedAudio, createdJobs, enqueued)

  protected def makeBlobStore(
    refs: IngestRefs,
    onStoreText: (UUID, String) => AppTask[String] = (jobId, _) => ZIO.succeed(s"text-$jobId"),
    onStoreAudio: (UUID, Array[Byte], String, String) => AppTask[String] = (jobId, _, _, _) =>
      ZIO.succeed(s"audio-$jobId")
  ): BlobStorePort =
    new BlobStorePort {
      override def storeAudio(
        jobId: UUID,
        audioContent: Array[Byte],
        mediaContentType: String,
        mediaFilename: String
      ): AppTask[String] =
        refs.storedAudio.update(_ :+ (jobId, mediaContentType, mediaFilename)) *> onStoreAudio(
          jobId,
          audioContent,
          mediaContentType,
          mediaFilename
        )

      override def storeText(jobId: UUID, textContent: String): AppTask[String] =
        refs.storedText.update(_ :+ (jobId, textContent)) *> onStoreText(jobId, textContent)

      override def fetchAudio(blobKey: String): AppTask[Array[Byte]] =
        unexpectedPipelineCall(s"BlobStorePort.fetchAudio($blobKey)")

      override def fetchBlobAsStream(blobKey: String): ZStream[Any, PipelineError, Byte] =
        ZStream.dieMessage(s"Unexpected test call to BlobStorePort.fetchBlobAsStream($blobKey)")

      override def getBlobFilename(blobKey: String): AppTask[Option[String]] =
        unexpectedPipelineCall(s"BlobStorePort.getBlobFilename($blobKey)")

      override def getBlobContentType(blobKey: String): AppTask[Option[String]] =
        unexpectedPipelineCall(s"BlobStorePort.getBlobContentType($blobKey)")

      override def deleteBlob(blobKey: String): AppTask[Unit] =
        unexpectedPipelineCall(s"BlobStorePort.deleteBlob($blobKey)")

      override def listAllBlobs(): AppTask[List[BlobInfo]] =
        unexpectedPipelineCall("BlobStorePort.listAllBlobs")

      override def healthCheck() = unexpectedTaskCall("BlobStorePort.healthCheck")
    }

  protected def makeRepository(refs: IngestRefs): IngestionJobRepository[AppTask] =
    new IngestionJobRepository[AppTask] {
      override def create(job: IngestionJob): AppTask[IngestionJob] =
        refs.createdJobs.update(_ :+ job).as(job)

      override def update(job: IngestionJob): AppTask[IngestionJob] =
        refs.createdJobs.update { jobs =>
          val withoutJob = jobs.filterNot(_.id == job.id)
          withoutJob :+ job
        }.as(job)

      override def findById(jobId: UUID): AppTask[Option[IngestionJob]] =
        refs.createdJobs.get.map(_.find(_.id == jobId))

      override def listRunnable(now: Instant, limit: Int): AppTask[List[IngestionJob]] =
        ZIO.succeed(List.empty)

      override def listAll(): AppTask[List[IngestionJob]] =
        refs.createdJobs.get.map(_.toList)
    }

  protected def makeQueue(
    refs: IngestRefs,
    onEnqueue: UUID => AppTask[Unit] = _ => ZIO.unit
  ): JobQueuePort =
    new JobQueuePort {
      override def enqueue(jobId: UUID): AppTask[Unit] =
        refs.enqueued.update(_ :+ jobId) *> onEnqueue(jobId)

      override def dequeueBatch(max: Int): AppTask[List[UUID]] =
        unexpectedPipelineCall(s"JobQueuePort.dequeueBatch($max)")

      override def claim(blockingTimeoutSec: Int): AppTask[Option[UUID]] =
        unexpectedPipelineCall(s"JobQueuePort.claim($blockingTimeoutSec)")

      override def heartbeat(jobId: UUID): AppTask[Unit] =
        unexpectedPipelineCall(s"JobQueuePort.heartbeat($jobId)")

      override def ack(jobId: UUID): AppTask[Unit] =
        unexpectedPipelineCall(s"JobQueuePort.ack($jobId)")

      override def release(jobId: UUID): AppTask[Unit] =
        unexpectedPipelineCall(s"JobQueuePort.release($jobId)")

      override def recoverStaleJobs(): AppTask[Int] =
        unexpectedPipelineCall("JobQueuePort.recoverStaleJobs")

      override def retry(jobId: UUID, attempt: Int, delay: zio.Duration): AppTask[Unit] =
        unexpectedPipelineCall(s"JobQueuePort.retry($jobId, $attempt, $delay)")

      override def deadLetter(jobId: UUID, reason: String): AppTask[Unit] =
        unexpectedPipelineCall(s"JobQueuePort.deadLetter($jobId)")

      override def healthCheck() = unexpectedTaskCall("JobQueuePort.healthCheck")
    }

  protected def ingestLayer(
    blobStore: BlobStorePort,
    repository: IngestionJobRepository[AppTask],
    queue: JobQueuePort
  ): URLayer[JobProcessingConfig, IngestService] =
    (
      ZLayer.succeed(blobStore) ++
        ZLayer.succeed(repository) ++
        ZLayer.succeed(queue)
    ) >>> IngestService.layer
}

trait SharedDomainMakers {
  protected def sampleTranscript(
    id: UUID = UUID.randomUUID(),
    source: IngestSource = IngestSource.Text,
    text: String = "hello world",
    metadata: Map[String, String] = Map.empty,
    confidence: Double = 1.0,
    createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z")
  ): Transcript = {
    val words = text
      .split("\\s+")
      .filter(_.nonEmpty)
      .zipWithIndex
      .map { case (word, idx) =>
        Word(text = word, start = idx.toLong, end = idx.toLong + 1L, confidence = 1.0)
      }
      .toList

    Transcript(
      id = id,
      language = None,
      words = words,
      confidence = confidence,
      createdAt = createdAt,
      source = source,
      metadata = metadata
    )
  }

  protected def sampleJob(
    id: UUID = UUID.randomUUID(),
    source: IngestSource = IngestSource.Text,
    status: JobStatus = JobStatus.Pending,
    attempt: Int = 0,
    maxAttempts: Int = ApplicationSharedLayers.sharedJobProcessingConfig.maxAttempts,
    blobKey: Option[String] = Some("blob-key"),
    mediaContentType: Option[String] = None,
    mediaFilename: Option[String] = None,
    transcriptId: Option[UUID] = None,
    metadata: Map[String, String] = Map.empty,
    errorMessage: Option[String] = None,
    createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    updatedAt: Instant = Instant.parse("2026-01-01T00:00:00Z")
  ): IngestionJob =
    IngestionJob(
      id = id,
      transcriptId = transcriptId,
      source = source,
      mediaContentType = mediaContentType,
      mediaFilename = mediaFilename,
      status = status,
      attempt = attempt,
      maxAttempts = maxAttempts,
      errorMessage = errorMessage,
      blobKey = blobKey,
      metadata = metadata,
      createdAt = createdAt,
      updatedAt = updatedAt
    )
}

trait SharedHealthCheckMakers {
  protected def healthStatus(
    serviceName: String,
    checkedAt: Instant = Instant.parse("2026-01-01T00:00:00Z")
  ): HealthStatus =
    HealthStatus.Healthy(serviceName = serviceName, checkedAt = checkedAt)

  protected def makeHealthTranscriber(status: Task[HealthStatus]): TranscriberPort =
    new TranscriberPort {
      override def transcribe(audioContent: Array[Byte], mediaContentType: String, mediaFilename: String) =
        unexpectedPipelineCall[Transcript](s"TranscriberPort.transcribe($mediaFilename)")

      override def healthCheck(): Task[HealthStatus] = status
    }

  protected def makeHealthEmbedder(status: Task[HealthStatus]): EmbedderPort =
    new EmbedderPort {
      override def embed(transcript: Transcript) =
        unexpectedPipelineCall[List[(String, Array[Float])]](s"EmbedderPort.embed(${transcript.id})")

      override def embedQuery(query: String) =
        unexpectedPipelineCall[Array[Float]](s"EmbedderPort.embedQuery($query)")

      override def healthCheck(): Task[HealthStatus] = status
    }

  protected def makeHealthDatasource(status: Task[HealthStatus]): DatasourcePort =
    new DatasourcePort {
      override def healthCheck(): Task[HealthStatus] = status
    }

  protected def makeHealthVectorStore(status: Task[HealthStatus]): VectorStorePort =
    new VectorStorePort {
      override def upsertEmbeddings(transcriptId: UUID, vectors: List[Array[Float]], metadata: Map[String, String]) =
        unexpectedPipelineCall[Unit](s"VectorStorePort.upsertEmbeddings($transcriptId)")

      override def searchSimilar(
        queryVector: Array[Float],
        limit: Int,
        filter: Option[VectorStoreFilter]
      ) =
        unexpectedPipelineCall[List[VectorSearchResult]](s"VectorStorePort.searchSimilar($limit)")

      override def listAllVectors() =
        unexpectedPipelineCall[List[VectorInfo]]("VectorStorePort.listAllVectors")

      override def healthCheck(): Task[HealthStatus] = status
    }

  protected def makeHealthLexicalStore(status: Task[HealthStatus]): LexicalStorePort =
    new LexicalStorePort {
      override def indexSegments(transcriptId: UUID, segments: List[(Int, String)], metadata: Map[String, String]) =
        unexpectedPipelineCall[Unit](s"LexicalStorePort.indexSegments($transcriptId)")

      override def deleteTranscript(transcriptId: UUID) =
        unexpectedPipelineCall[Unit](s"LexicalStorePort.deleteTranscript($transcriptId)")

      override def search(queryText: String, limit: Int, filter: Option[VectorStoreFilter]) =
        unexpectedPipelineCall[List[LexicalSearchResult]](s"LexicalStorePort.search($queryText)")

      override def listAllDocuments() =
        unexpectedPipelineCall[List[DocumentInfo]]("LexicalStorePort.listAllDocuments")

      override def healthCheck(): Task[HealthStatus] = status
    }

  protected def makeHealthReranker(status: Task[HealthStatus]): RerankerPort =
    new RerankerPort {
      override def rerank(query: String, candidates: List[RerankerCandidate], topK: Int) =
        unexpectedPipelineCall[List[RerankerResult]](s"RerankerPort.rerank($query, $topK)")

      override def healthCheck(): Task[HealthStatus] = status
    }

  protected def makeHealthBlobStore(status: Task[HealthStatus]): BlobStorePort =
    new BlobStorePort {
      override def storeAudio(jobId: UUID, audioContent: Array[Byte], mediaContentType: String, mediaFilename: String) =
        unexpectedPipelineCall[String](s"BlobStorePort.storeAudio($jobId)")

      override def storeText(jobId: UUID, textContent: String) =
        unexpectedPipelineCall[String](s"BlobStorePort.storeText($jobId)")

      override def fetchAudio(blobKey: String) =
        unexpectedPipelineCall[Array[Byte]](s"BlobStorePort.fetchAudio($blobKey)")

      override def fetchBlobAsStream(blobKey: String): ZStream[Any, PipelineError, Byte] =
        ZStream.dieMessage(s"Unexpected test call to BlobStorePort.fetchBlobAsStream($blobKey)")

      override def getBlobFilename(blobKey: String) =
        unexpectedPipelineCall[Option[String]](s"BlobStorePort.getBlobFilename($blobKey)")

      override def getBlobContentType(blobKey: String) =
        unexpectedPipelineCall[Option[String]](s"BlobStorePort.getBlobContentType($blobKey)")

      override def deleteBlob(blobKey: String) =
        unexpectedPipelineCall[Unit](s"BlobStorePort.deleteBlob($blobKey)")

      override def listAllBlobs() =
        unexpectedPipelineCall[List[BlobInfo]]("BlobStorePort.listAllBlobs")

      override def healthCheck(): Task[HealthStatus] = status
    }

  protected def makeHealthQueue(status: Task[HealthStatus]): JobQueuePort =
    new JobQueuePort {
      override def enqueue(jobId: UUID) = unexpectedPipelineCall[Unit](s"JobQueuePort.enqueue($jobId)")

      override def dequeueBatch(max: Int) = unexpectedPipelineCall[List[UUID]](s"JobQueuePort.dequeueBatch($max)")

      override def claim(blockingTimeoutSec: Int) =
        unexpectedPipelineCall[Option[UUID]](s"JobQueuePort.claim($blockingTimeoutSec)")

      override def heartbeat(jobId: UUID) = unexpectedPipelineCall[Unit](s"JobQueuePort.heartbeat($jobId)")

      override def ack(jobId: UUID) = unexpectedPipelineCall[Unit](s"JobQueuePort.ack($jobId)")

      override def release(jobId: UUID) = unexpectedPipelineCall[Unit](s"JobQueuePort.release($jobId)")

      override def recoverStaleJobs() = unexpectedPipelineCall[Int]("JobQueuePort.recoverStaleJobs")

      override def retry(jobId: UUID, attempt: Int, delay: zio.Duration) =
        unexpectedPipelineCall[Unit](s"JobQueuePort.retry($jobId, $attempt, $delay)")

      override def deadLetter(jobId: UUID, reason: String) =
        unexpectedPipelineCall[Unit](s"JobQueuePort.deadLetter($jobId)")

      override def healthCheck(): Task[HealthStatus] = status
    }

  protected def healthLayer(
    transcriber: TranscriberPort,
    embedder: EmbedderPort,
    datasource: DatasourcePort,
    vectorStore: VectorStorePort,
    lexicalStore: LexicalStorePort,
    reranker: RerankerPort,
    blobStore: BlobStorePort,
    queue: JobQueuePort
  ): ULayer[HealthCheckService] =
    (
      ZLayer.succeed(transcriber) ++
        ZLayer.succeed(embedder) ++
        ZLayer.succeed(datasource) ++
        ZLayer.succeed(vectorStore) ++
        ZLayer.succeed(lexicalStore) ++
        ZLayer.succeed(reranker) ++
        ZLayer.succeed(blobStore) ++
        ZLayer.succeed(queue)
    ) >>> HealthCheckService.layer
}

trait SharedIndexingMakers {
  protected def makeIndexingRefs: UIO[IndexingRefs] =
    for {
      persisted       <- Ref.make(Vector.empty[UUID])
      embedded        <- Ref.make(Vector.empty[UUID])
      upserts         <- Ref.make(Vector.empty[(UUID, Int, Map[String, String])])
      deletions       <- Ref.make(Vector.empty[UUID])
      indexedSegments <- Ref.make(Vector.empty[(UUID, List[(Int, String)], Map[String, String])])
    } yield IndexingRefs(
      persisted = persisted,
      embedded = embedded,
      upserts = upserts,
      deletions = deletions,
      indexedSegments = indexedSegments
    )

  protected def makeIndexingRepository(
    refs: IndexingRefs,
    onPersist: Transcript => AppTask[Unit] = _ => ZIO.unit
  ): TranscriptRepository[AppTask] =
    new TranscriptRepository[AppTask] {
      override def persist(transcript: Transcript): AppTask[Unit] =
        refs.persisted.update(_ :+ transcript.id) *> onPersist(transcript)

      override def getAll(): AppTask[List[Transcript]] =
        unexpectedPipelineCall("TranscriptRepository.getAll")

      override def getById(id: UUID): AppTask[Option[Transcript]] =
        unexpectedPipelineCall(s"TranscriptRepository.getById($id)")
    }

  protected def makeIndexingEmbedder(
    refs: IndexingRefs,
    onEmbed: Transcript => AppTask[List[(String, Array[Float])]]
  ): EmbedderPort =
    new EmbedderPort {
      override def embed(transcript: Transcript): AppTask[List[(String, Array[Float])]] =
        refs.embedded.update(_ :+ transcript.id) *> onEmbed(transcript)

      override def embedQuery(query: String): AppTask[Array[Float]] =
        unexpectedPipelineCall(s"EmbedderPort.embedQuery($query)")

      override def healthCheck() = unexpectedTaskCall("EmbedderPort.healthCheck")
    }

  protected def makeIndexingVectorStore(
    refs: IndexingRefs,
    onUpsert: (UUID, List[Array[Float]], Map[String, String]) => AppTask[Unit] = (_, _, _) => ZIO.unit
  ): VectorStorePort =
    new VectorStorePort {
      override def upsertEmbeddings(
        transcriptId: UUID,
        vectors: List[Array[Float]],
        metadata: Map[String, String]
      ): AppTask[Unit] =
        refs.upserts.update(_ :+ (transcriptId, vectors.size, metadata)) *> onUpsert(transcriptId, vectors, metadata)

      override def searchSimilar(
        queryVector: Array[Float],
        limit: Int,
        filter: Option[VectorStoreFilter]
      ): AppTask[List[VectorSearchResult]] =
        unexpectedPipelineCall(s"VectorStorePort.searchSimilar($limit)")

      override def listAllVectors(): AppTask[List[VectorInfo]] =
        unexpectedPipelineCall("VectorStorePort.listAllVectors")

      override def healthCheck() = unexpectedTaskCall("VectorStorePort.healthCheck")
    }

  protected def makeIndexingLexicalStore(
    refs: IndexingRefs,
    onDelete: UUID => AppTask[Unit] = _ => ZIO.unit,
    onIndex: (UUID, List[(Int, String)], Map[String, String]) => AppTask[Unit] = (_, _, _) => ZIO.unit
  ): LexicalStorePort =
    new LexicalStorePort {
      override def indexSegments(
        transcriptId: UUID,
        segments: List[(Int, String)],
        metadata: Map[String, String]
      ): AppTask[Unit] =
        refs.indexedSegments
          .update(_ :+ (transcriptId, segments, metadata)) *> onIndex(transcriptId, segments, metadata)

      override def deleteTranscript(transcriptId: UUID): AppTask[Unit] =
        refs.deletions.update(_ :+ transcriptId) *> onDelete(transcriptId)

      override def search(
        queryText: String,
        limit: Int,
        filter: Option[VectorStoreFilter]
      ): AppTask[List[LexicalSearchResult]] =
        unexpectedPipelineCall(s"LexicalStorePort.search($queryText)")

      override def listAllDocuments(): AppTask[List[DocumentInfo]] =
        unexpectedPipelineCall("LexicalStorePort.listAllDocuments")

      override def healthCheck() = unexpectedTaskCall("LexicalStorePort.healthCheck")
    }

  protected def indexingLayer(
    repository: TranscriptRepository[AppTask],
    embedder: EmbedderPort,
    vectorStore: VectorStorePort,
    lexicalStore: LexicalStorePort
  ): ULayer[IndexingPipeline] =
    (
      ZLayer.succeed(repository) ++
        ZLayer.succeed(embedder) ++
        ZLayer.succeed(vectorStore) ++
        ZLayer.succeed(lexicalStore)
    ) >>> IndexingPipeline.layer
}

trait SharedPreparatorMakers {
  protected def makeAudioPreparatorRefs: UIO[AudioPreparatorRefs] =
    for {
      fetchedBlobKeys <- Ref.make(Vector.empty[String])
      transcribeCalls <- Ref.make(Vector.empty[(List[Byte], String, String)])
    } yield AudioPreparatorRefs(
      fetchedBlobKeys = fetchedBlobKeys,
      transcribeCalls = transcribeCalls
    )

  protected def makeAudioBlobStore(
    refs: AudioPreparatorRefs,
    onFetchAudio: String => AppTask[Array[Byte]]
  ): BlobStorePort =
    new BlobStorePort {
      override def storeAudio(jobId: UUID, audioContent: Array[Byte], mediaContentType: String, mediaFilename: String) =
        unexpectedPipelineCall[String](s"BlobStorePort.storeAudio($jobId)")

      override def storeText(jobId: UUID, textContent: String) =
        unexpectedPipelineCall[String](s"BlobStorePort.storeText($jobId)")

      override def fetchAudio(blobKey: String): AppTask[Array[Byte]] =
        refs.fetchedBlobKeys.update(_ :+ blobKey) *> onFetchAudio(blobKey)

      override def fetchBlobAsStream(blobKey: String): ZStream[Any, PipelineError, Byte] =
        ZStream.dieMessage(s"Unexpected test call to BlobStorePort.fetchBlobAsStream($blobKey)")

      override def getBlobFilename(blobKey: String): AppTask[Option[String]] =
        unexpectedPipelineCall(s"BlobStorePort.getBlobFilename($blobKey)")

      override def getBlobContentType(blobKey: String): AppTask[Option[String]] =
        unexpectedPipelineCall(s"BlobStorePort.getBlobContentType($blobKey)")

      override def deleteBlob(blobKey: String): AppTask[Unit] =
        unexpectedPipelineCall(s"BlobStorePort.deleteBlob($blobKey)")

      override def listAllBlobs(): AppTask[List[BlobInfo]] =
        unexpectedPipelineCall("BlobStorePort.listAllBlobs")

      override def healthCheck() = unexpectedTaskCall("BlobStorePort.healthCheck")
    }

  protected def makeAudioTranscriber(
    refs: AudioPreparatorRefs,
    onTranscribe: (Array[Byte], String, String) => AppTask[Transcript]
  ): TranscriberPort =
    new TranscriberPort {
      override def transcribe(
        audioContent: Array[Byte],
        mediaContentType: String,
        mediaFilename: String
      ): AppTask[Transcript] =
        refs.transcribeCalls
          .update(_ :+ (audioContent.toList, mediaContentType, mediaFilename)) *> onTranscribe(
          audioContent,
          mediaContentType,
          mediaFilename
        )

      override def healthCheck() = unexpectedTaskCall("TranscriberPort.healthCheck")
    }

  protected def audioPreparatorLayer(
    blobStore: BlobStorePort,
    transcriber: TranscriberPort
  ): ULayer[AudioPreparatorPipeline] =
    (ZLayer.succeed(blobStore) ++ ZLayer.succeed(transcriber)) >>> AudioPreparatorPipeline.layer

  protected def makeTextPreparatorRefs: UIO[TextPreparatorRefs] =
    Ref.make(Vector.empty[String]).map(TextPreparatorRefs.apply)

  protected def makeTextBlobStore(
    refs: TextPreparatorRefs,
    onFetchAudio: String => AppTask[Array[Byte]]
  ): BlobStorePort =
    new BlobStorePort {
      override def storeAudio(jobId: UUID, audioContent: Array[Byte], mediaContentType: String, mediaFilename: String) =
        unexpectedPipelineCall[String](s"BlobStorePort.storeAudio($jobId)")

      override def storeText(jobId: UUID, textContent: String) =
        unexpectedPipelineCall[String](s"BlobStorePort.storeText($jobId)")

      override def fetchAudio(blobKey: String): AppTask[Array[Byte]] =
        refs.fetchedBlobKeys.update(_ :+ blobKey) *> onFetchAudio(blobKey)

      override def fetchBlobAsStream(blobKey: String): ZStream[Any, PipelineError, Byte] =
        ZStream.dieMessage(s"Unexpected test call to BlobStorePort.fetchBlobAsStream($blobKey)")

      override def getBlobFilename(blobKey: String): AppTask[Option[String]] =
        unexpectedPipelineCall(s"BlobStorePort.getBlobFilename($blobKey)")

      override def getBlobContentType(blobKey: String): AppTask[Option[String]] =
        unexpectedPipelineCall(s"BlobStorePort.getBlobContentType($blobKey)")

      override def deleteBlob(blobKey: String): AppTask[Unit] =
        unexpectedPipelineCall(s"BlobStorePort.deleteBlob($blobKey)")

      override def listAllBlobs(): AppTask[List[BlobInfo]] =
        unexpectedPipelineCall("BlobStorePort.listAllBlobs")

      override def healthCheck() = unexpectedTaskCall("BlobStorePort.healthCheck")
    }

  protected def textPreparatorLayer(blobStore: BlobStorePort): ULayer[TextPreparatorPipeline] =
    ZLayer.succeed(blobStore) >>> TextPreparatorPipeline.layer

  protected def makeRouterRefs: UIO[RouterRefs] =
    for {
      audioCalls <- Ref.make(Vector.empty[UUID])
      textCalls  <- Ref.make(Vector.empty[UUID])
    } yield RouterRefs(audioCalls = audioCalls, textCalls = textCalls)

  protected def makeAudioPreparator(
    refs: RouterRefs,
    onPrepare: IngestionJob => AppTask[Transcript]
  ): AudioPreparatorPipeline =
    new AudioPreparatorPipeline {
      override def prepare(job: IngestionJob): AppTask[Transcript] =
        refs.audioCalls.update(_ :+ job.id) *> onPrepare(job)
    }

  protected def makeTextPreparator(
    refs: RouterRefs,
    onPrepare: IngestionJob => AppTask[Transcript]
  ): TextPreparatorPipeline =
    new TextPreparatorPipeline {
      override def prepare(job: IngestionJob): AppTask[Transcript] =
        refs.textCalls.update(_ :+ job.id) *> onPrepare(job)
    }

  protected def routerLayer(
    audioPreparator: AudioPreparatorPipeline,
    textPreparator: TextPreparatorPipeline
  ): ULayer[PreparatorRouterPipeline] =
    (ZLayer.succeed(audioPreparator) ++ ZLayer.succeed(textPreparator)) >>> PreparatorRouterPipeline.layer
}

trait SharedWorkerMakers {
  protected def makeWorkerRefs(initialJobs: Map[UUID, IngestionJob]): UIO[WorkerRefs] =
    for {
      jobs           <- Ref.make(initialJobs)
      updates        <- Ref.make(Vector.empty[IngestionJob])
      preparedJobs   <- Ref.make(Vector.empty[UUID])
      indexedJobs    <- Ref.make(Vector.empty[UUID])
      deletedBlobs   <- Ref.make(Vector.empty[String])
      claimCalls     <- Ref.make(Vector.empty[Int])
      heartbeatCalls <- Ref.make(Vector.empty[UUID])
      acked          <- Ref.make(Vector.empty[UUID])
      released       <- Ref.make(Vector.empty[UUID])
      enqueued       <- Ref.make(Vector.empty[UUID])
      deadLettered   <- Ref.make(Vector.empty[(UUID, String)])
    } yield WorkerRefs(
      jobs = jobs,
      updates = updates,
      preparedJobs = preparedJobs,
      indexedJobs = indexedJobs,
      deletedBlobs = deletedBlobs,
      claimCalls = claimCalls,
      heartbeatCalls = heartbeatCalls,
      acked = acked,
      released = released,
      enqueued = enqueued,
      deadLettered = deadLettered
    )

  protected def makeWorkerRepository(
    refs: WorkerRefs,
    onUpdate: IngestionJob => AppTask[IngestionJob] = job => ZIO.succeed(job),
    onFindById: Option[UUID => AppTask[Option[IngestionJob]]] = None
  ): IngestionJobRepository[AppTask] =
    new IngestionJobRepository[AppTask] {
      override def create(job: IngestionJob): AppTask[IngestionJob] =
        unexpectedPipelineCall(s"IngestionJobRepository.create(${job.id})")

      override def update(job: IngestionJob): AppTask[IngestionJob] =
        onUpdate(job).flatMap { persisted =>
          refs.updates.update(_ :+ persisted) *>
            refs.jobs.update(_.updated(persisted.id, persisted)).as(persisted)
        }

      override def findById(jobId: UUID): AppTask[Option[IngestionJob]] =
        onFindById match {
          case Some(findByIdFn) => findByIdFn(jobId)
          case None             => refs.jobs.get.map(_.get(jobId))
        }

      override def listRunnable(now: Instant, limit: Int): AppTask[List[IngestionJob]] =
        unexpectedPipelineCall("IngestionJobRepository.listRunnable")

      override def listAll(): AppTask[List[IngestionJob]] =
        refs.jobs.get.map(_.values.toList)
    }

  protected def makeWorkerBlobStore(
    refs: WorkerRefs,
    onDeleteBlob: String => AppTask[Unit] = _ => ZIO.unit
  ): BlobStorePort =
    new BlobStorePort {
      override def storeAudio(jobId: UUID, audioContent: Array[Byte], mediaContentType: String, mediaFilename: String) =
        unexpectedPipelineCall[String](s"BlobStorePort.storeAudio($jobId)")

      override def storeText(jobId: UUID, textContent: String) =
        unexpectedPipelineCall[String](s"BlobStorePort.storeText($jobId)")

      override def fetchAudio(blobKey: String): AppTask[Array[Byte]] =
        unexpectedPipelineCall(s"BlobStorePort.fetchAudio($blobKey)")

      override def fetchBlobAsStream(blobKey: String): ZStream[Any, PipelineError, Byte] =
        ZStream.dieMessage(s"Unexpected test call to BlobStorePort.fetchBlobAsStream($blobKey)")

      override def getBlobFilename(blobKey: String): AppTask[Option[String]] =
        unexpectedPipelineCall(s"BlobStorePort.getBlobFilename($blobKey)")

      override def getBlobContentType(blobKey: String): AppTask[Option[String]] =
        unexpectedPipelineCall(s"BlobStorePort.getBlobContentType($blobKey)")

      override def deleteBlob(blobKey: String): AppTask[Unit] =
        refs.deletedBlobs.update(_ :+ blobKey) *> onDeleteBlob(blobKey)

      override def listAllBlobs(): AppTask[List[BlobInfo]] =
        unexpectedPipelineCall("BlobStorePort.listAllBlobs")

      override def healthCheck() = unexpectedTaskCall("BlobStorePort.healthCheck")
    }

  protected def makeWorkerPreparatorRouter(
    refs: WorkerRefs,
    onPrepare: IngestionJob => AppTask[Transcript]
  ): PreparatorRouterPipeline =
    new PreparatorRouterPipeline {
      override def dispatchAndPrepare(job: IngestionJob): AppTask[Transcript] =
        refs.preparedJobs.update(_ :+ job.id) *> onPrepare(job)
    }

  protected def makeWorkerIndexingPipeline(
    refs: WorkerRefs,
    onIndex: (Transcript, IngestionJob) => AppTask[Unit] = (_, _) => ZIO.unit
  ): IndexingPipeline =
    new IndexingPipeline {
      override def index(transcript: Transcript, job: IngestionJob): AppTask[Unit] =
        refs.indexedJobs.update(_ :+ job.id) *> onIndex(transcript, job)
    }

  protected def makeWorkerQueue(
    refs: WorkerRefs,
    claimedJobId: UUID,
    onAck: UUID => AppTask[Unit] = _ => ZIO.unit,
    onRelease: UUID => AppTask[Unit] = _ => ZIO.unit,
    onEnqueue: UUID => AppTask[Unit] = _ => ZIO.unit,
    onDeadLetter: (UUID, String) => AppTask[Unit] = (_, _) => ZIO.unit
  ): UIO[JobQueuePort] =
    Ref.make(false).map { alreadyClaimed =>
      new JobQueuePort {
        override def enqueue(jobId: UUID): AppTask[Unit] =
          refs.enqueued.update(_ :+ jobId) *> onEnqueue(jobId)

        override def dequeueBatch(max: Int): AppTask[List[UUID]] =
          unexpectedPipelineCall(s"JobQueuePort.dequeueBatch($max)")

        override def claim(blockingTimeoutSec: Int): AppTask[Option[UUID]] =
          refs.claimCalls.update(_ :+ blockingTimeoutSec) *>
            alreadyClaimed.getAndSet(true).flatMap {
              case false => ZIO.succeed(Some(claimedJobId))
              case true  => ZIO.never
            }

        override def heartbeat(jobId: UUID): AppTask[Unit] =
          refs.heartbeatCalls.update(_ :+ jobId)

        override def ack(jobId: UUID): AppTask[Unit] =
          refs.acked.update(_ :+ jobId) *> onAck(jobId)

        override def release(jobId: UUID): AppTask[Unit] =
          refs.released.update(_ :+ jobId) *> onRelease(jobId)

        override def recoverStaleJobs(): AppTask[Int] =
          unexpectedPipelineCall("JobQueuePort.recoverStaleJobs")

        override def retry(jobId: UUID, attempt: Int, delay: zio.Duration): AppTask[Unit] =
          unexpectedPipelineCall(s"JobQueuePort.retry($jobId, $attempt, $delay)")

        override def deadLetter(jobId: UUID, reason: String): AppTask[Unit] =
          refs.deadLettered.update(_ :+ (jobId, reason)) *> onDeadLetter(jobId, reason)

        override def healthCheck() = unexpectedTaskCall("JobQueuePort.healthCheck")
      }
    }

  protected def workerLayer(
    repository: IngestionJobRepository[AppTask],
    blobStore: BlobStorePort,
    preparatorRouter: PreparatorRouterPipeline,
    indexingPipeline: IndexingPipeline,
    queue: JobQueuePort
  ): URLayer[JobProcessingConfig, IngestionWorker] =
    (
      ZLayer.succeed(repository) ++
        ZLayer.succeed(blobStore) ++
        ZLayer.succeed(preparatorRouter) ++
        ZLayer.succeed(indexingPipeline) ++
        ZLayer.succeed(queue)
    ) >>> IngestionWorker.layer
}

trait SharedQueryMakers {
  protected def makeTranscriptRepository(
    transcripts: Map[UUID, Transcript]
  ): TranscriptRepository[AppTask] =
    new TranscriptRepository[AppTask] {
      override def persist(transcript: Transcript): AppTask[Unit] =
        unexpectedPipelineCall(s"TranscriptRepository.persist(${transcript.id})")

      override def getAll(): AppTask[List[Transcript]] =
        ZIO.succeed(transcripts.values.toList)

      override def getById(id: UUID): AppTask[Option[Transcript]] =
        ZIO.succeed(transcripts.get(id))
    }

  protected def queryLayer(
    embedder: EmbedderPort,
    vectorStore: VectorStorePort,
    lexicalStore: LexicalStorePort,
    reranker: RerankerPort,
    repository: TranscriptRepository[AppTask]
  ): ULayer[QueryService] =
    (
      ZLayer.succeed(embedder) ++
        ZLayer.succeed(vectorStore) ++
        ZLayer.succeed(lexicalStore) ++
        ZLayer.succeed(reranker) ++
        ZLayer.succeed(repository)
    ) >>> QueryService.layer
}

abstract class ApplicationSharedSpec
    extends ZIOSpec[JobProcessingConfig]
    with SharedPortMakers
    with SharedIngestionMakers
    with SharedDomainMakers
    with SharedHealthCheckMakers
    with SharedIndexingMakers
    with SharedPreparatorMakers
    with SharedWorkerMakers
    with SharedQueryMakers {
  override val bootstrap: ULayer[JobProcessingConfig] =
    ApplicationSharedLayers.bootstrap
}
