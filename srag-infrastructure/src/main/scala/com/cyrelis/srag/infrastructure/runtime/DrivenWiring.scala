package com.cyrelis.srag.infrastructure.runtime

import com.cyrelis.srag.application.ports.{
  BlobStorePort,
  EmbedderPort,
  JobQueuePort,
  LexicalStorePort,
  RerankerPort,
  TranscriberPort,
  VectorStorePort
}
import com.cyrelis.srag.infrastructure.config.{AdapterFactory, RuntimeConfig}
import zio.*

trait DrivenWiring {
  val transcriberLayer: ZLayer[RuntimeConfig, Throwable, TranscriberPort] =
    ZLayer.fromZIO(ZIO.service[RuntimeConfig]).flatMap { env =>
      val config = env.get[RuntimeConfig]
      AdapterFactory.createTranscriberLayer(config.adapters.driven.transcriber)
    }

  val embedderLayer: ZLayer[RuntimeConfig, Throwable, EmbedderPort] =
    ZLayer.fromZIO(ZIO.service[RuntimeConfig]).flatMap { env =>
      val config = env.get[RuntimeConfig]
      AdapterFactory.createEmbedderLayer(config.adapters.driven.embedder)
    }

  val vectorSinkLayer: ZLayer[RuntimeConfig, Throwable, VectorStorePort] =
    ZLayer.fromZIO(ZIO.service[RuntimeConfig]).flatMap { env =>
      val config = env.get[RuntimeConfig]
      AdapterFactory.createVectorStoreLayer(config.adapters.driven.vectorStore)
    }

  val lexicalStoreLayer: ZLayer[RuntimeConfig, Throwable, LexicalStorePort] =
    ZLayer.fromZIO(ZIO.service[RuntimeConfig]).flatMap { env =>
      val config = env.get[RuntimeConfig]
      AdapterFactory.createLexicalStoreLayer(config.adapters.driven.lexicalStore)
    }

  val rerankerLayer: ZLayer[RuntimeConfig, Throwable, RerankerPort] =
    ZLayer.fromZIO(ZIO.service[RuntimeConfig]).flatMap { env =>
      val config = env.get[RuntimeConfig]
      AdapterFactory.createRerankerLayer(config.adapters.driven.reranker)
    }

  val blobStoreLayer: ZLayer[RuntimeConfig, Throwable, BlobStorePort] =
    ZLayer.fromZIO(ZIO.service[RuntimeConfig]).flatMap { env =>
      val config = env.get[RuntimeConfig]
      AdapterFactory.createBlobStoreLayer(config.adapters.driven.blobStore)
    }

  val jobQueueLayer: ZLayer[RuntimeConfig, Throwable, JobQueuePort] =
    ZLayer.fromZIO(ZIO.service[RuntimeConfig]).flatMap { env =>
      val config = env.get[RuntimeConfig]
      AdapterFactory.createJobQueueLayer(config.adapters.driven.jobQueue)
    }
}

object DrivenModule extends DrivenWiring {
  type DrivenEnvironment = TranscriberPort & EmbedderPort & VectorStorePort & LexicalStorePort & RerankerPort &
    BlobStorePort & JobQueuePort

  val live: ZLayer[RuntimeConfig, Throwable, DrivenEnvironment] = ZLayer.makeSome[RuntimeConfig, DrivenEnvironment](
    transcriberLayer,
    embedderLayer,
    vectorSinkLayer,
    lexicalStoreLayer,
    rerankerLayer,
    blobStoreLayer,
    jobQueueLayer
  )
}
