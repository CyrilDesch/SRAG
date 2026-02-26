package com.cyrelis.srag.infrastructure.runtime

import com.cyrelis.srag.application.ports.{
  BlobStorePort,
  DocumentParserPort,
  EmbedderPort,
  JobQueuePort,
  LexicalStorePort,
  RerankerPort,
  TranscriberPort,
  VectorStorePort
}
import com.cyrelis.srag.infrastructure.config.{AdapterFactory, RuntimeConfig}
import com.cyrelis.srag.infrastructure.resilience.RetryWrappers
import zio.*

trait DrivenWiring {
  val transcriberLayer: ZLayer[RuntimeConfig, Throwable, TranscriberPort] =
    ZLayer.fromZIO(ZIO.service[RuntimeConfig]).flatMap { env =>
      val config = env.get[RuntimeConfig]
      AdapterFactory.createTranscriberLayer(config.adapters.driven.transcriber).map { portEnv =>
        ZEnvironment(RetryWrappers.wrapTranscriber(portEnv.get, config.retry, config.timeouts))
      }
    }

  val embedderLayer: ZLayer[RuntimeConfig, Throwable, EmbedderPort] =
    ZLayer.fromZIO(ZIO.service[RuntimeConfig]).flatMap { env =>
      val config = env.get[RuntimeConfig]
      AdapterFactory.createEmbedderLayer(config.adapters.driven.embedder).map { portEnv =>
        ZEnvironment(RetryWrappers.wrapEmbedder(portEnv.get, config.retry, config.timeouts))
      }
    }

  val vectorSinkLayer: ZLayer[RuntimeConfig, Throwable, VectorStorePort] =
    ZLayer.fromZIO(ZIO.service[RuntimeConfig]).flatMap { env =>
      val config = env.get[RuntimeConfig]
      AdapterFactory.createVectorStoreLayer(config.adapters.driven.vectorStore).map { portEnv =>
        ZEnvironment(RetryWrappers.wrapVectorSink(portEnv.get, config.retry, config.timeouts))
      }
    }

  val lexicalStoreLayer: ZLayer[RuntimeConfig, Throwable, LexicalStorePort] =
    ZLayer.fromZIO(ZIO.service[RuntimeConfig]).flatMap { env =>
      val config = env.get[RuntimeConfig]
      AdapterFactory.createLexicalStoreLayer(config.adapters.driven.lexicalStore).map { portEnv =>
        ZEnvironment(RetryWrappers.wrapLexicalStore(portEnv.get, config.retry, config.timeouts))
      }
    }

  val rerankerLayer: ZLayer[RuntimeConfig, Throwable, RerankerPort] =
    ZLayer.fromZIO(ZIO.service[RuntimeConfig]).flatMap { env =>
      val config = env.get[RuntimeConfig]
      AdapterFactory.createRerankerLayer(config.adapters.driven.reranker).map { portEnv =>
        ZEnvironment(RetryWrappers.wrapReranker(portEnv.get, config.retry, config.timeouts))
      }
    }

  val blobStoreLayer: ZLayer[RuntimeConfig, Throwable, BlobStorePort] =
    ZLayer.fromZIO(ZIO.service[RuntimeConfig]).flatMap { env =>
      val config = env.get[RuntimeConfig]
      AdapterFactory.createBlobStoreLayer(config.adapters.driven.blobStore).map { portEnv =>
        ZEnvironment(RetryWrappers.wrapBlobStore(portEnv.get, config.retry, config.timeouts))
      }
    }

  val documentParserLayer: ZLayer[RuntimeConfig, Throwable, DocumentParserPort] =
    ZLayer.fromZIO(ZIO.service[RuntimeConfig]).flatMap { env =>
      val config = env.get[RuntimeConfig]
      AdapterFactory.createDocumentParserLayer().map { portEnv =>
        ZEnvironment(RetryWrappers.wrapDocumentParser(portEnv.get, config.retry, config.timeouts))
      }
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
