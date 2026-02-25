package com.cyrelis.srag.infrastructure.runtime

import com.cyrelis.srag.application.ports.driven.embedding.EmbedderPort
import com.cyrelis.srag.application.ports.driven.job.JobQueuePort
import com.cyrelis.srag.application.ports.driven.parser.DocumentParserPort
import com.cyrelis.srag.application.ports.driven.reranker.RerankerPort
import com.cyrelis.srag.application.ports.driven.storage.{BlobStorePort, LexicalStorePort, VectorStorePort}
import com.cyrelis.srag.application.ports.driven.transcription.TranscriberPort
import com.cyrelis.srag.infrastructure.config.{AdapterFactory, RuntimeConfig}
import com.cyrelis.srag.infrastructure.resilience.RetryWrappers
import zio.*

trait DrivenWiring {
  val transcriberLayer: ZLayer[RuntimeConfig, Nothing, TranscriberPort] =
    ZLayer {
      for {
        config         <- ZIO.service[RuntimeConfig]
        baseTranscriber = AdapterFactory.createTranscriberAdapter(config.adapters.driven.transcriber)
        transcriber     = RetryWrappers.wrapTranscriber(baseTranscriber, config.retry, config.timeouts)
      } yield transcriber
    }

  val embedderLayer: ZLayer[RuntimeConfig, Nothing, EmbedderPort] =
    ZLayer {
      for {
        config      <- ZIO.service[RuntimeConfig]
        baseEmbedder = AdapterFactory.createEmbedderAdapter(config.adapters.driven.embedder)
        embedder     = RetryWrappers.wrapEmbedder(baseEmbedder, config.retry, config.timeouts)
      } yield embedder
    }

  val vectorSinkLayer: ZLayer[RuntimeConfig, Nothing, VectorStorePort] =
    ZLayer {
      for {
        config        <- ZIO.service[RuntimeConfig]
        baseVectorSink = AdapterFactory.createVectorStoreAdapter(config.adapters.driven.vectorStore)
        vectorSink     = RetryWrappers.wrapVectorSink(baseVectorSink, config.retry, config.timeouts)
      } yield vectorSink
    }

  val lexicalStoreLayer: ZLayer[RuntimeConfig, Nothing, LexicalStorePort] =
    ZLayer {
      for {
        config          <- ZIO.service[RuntimeConfig]
        baseLexicalStore = AdapterFactory.createLexicalStoreAdapter(config.adapters.driven.lexicalStore)
        lexicalStore     = RetryWrappers.wrapLexicalStore(baseLexicalStore, config.retry, config.timeouts)
      } yield lexicalStore
    }

  val rerankerLayer: ZLayer[RuntimeConfig, Nothing, RerankerPort] =
    ZLayer {
      for {
        config      <- ZIO.service[RuntimeConfig]
        baseReranker = AdapterFactory.createRerankerAdapter(config.adapters.driven.reranker)
        reranker     = RetryWrappers.wrapReranker(baseReranker, config.retry, config.timeouts)
      } yield reranker
    }

  val blobStoreLayer: ZLayer[RuntimeConfig, Nothing, BlobStorePort] =
    ZLayer {
      for {
        config       <- ZIO.service[RuntimeConfig]
        baseBlobStore = AdapterFactory.createBlobStoreAdapter(config.adapters.driven.blobStore)
        blobStore     = RetryWrappers.wrapBlobStore(baseBlobStore, config.retry, config.timeouts)
      } yield blobStore
    }

  val documentParserLayer: ZLayer[RuntimeConfig, Nothing, DocumentParserPort] =
    ZLayer {
      for {
        config            <- ZIO.service[RuntimeConfig]
        baseDocumentParser = AdapterFactory.createDocumentParserAdapter()
        documentParser     = RetryWrappers.wrapDocumentParser(baseDocumentParser, config.retry, config.timeouts)
      } yield documentParser
    }

  val jobQueueLayer: ZLayer[RuntimeConfig, Throwable, JobQueuePort] =
    ZLayer.fromZIO(ZIO.service[RuntimeConfig]).flatMap { env =>
      val config = env.get[RuntimeConfig]
      AdapterFactory.createJobQueueLayer(config.adapters.driven.jobQueue)
    }
}

object DrivenModule extends DrivenWiring {
  type DrivenEnvironment = TranscriberPort & EmbedderPort & VectorStorePort & LexicalStorePort & RerankerPort &
    BlobStorePort & DocumentParserPort & JobQueuePort

  val live: ZLayer[RuntimeConfig, Throwable, DrivenEnvironment] = ZLayer.makeSome[RuntimeConfig, DrivenEnvironment](
    transcriberLayer,
    embedderLayer,
    vectorSinkLayer,
    lexicalStoreLayer,
    rerankerLayer,
    blobStoreLayer,
    documentParserLayer,
    jobQueueLayer
  )
}
