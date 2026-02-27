package com.cyrelis.srag.infrastructure.migration

import com.cyrelis.srag.application.ports.DatasourcePort
import com.cyrelis.srag.infrastructure.adapters.driven.database.postgres.QuillDatasource
import com.cyrelis.srag.infrastructure.config.{BlobStoreAdapterConfig, RuntimeConfig, VectorStoreAdapterConfig}
import zio.*

/**
 * Orchestrates all migration and initialization tasks at application startup.
 * This runs BEFORE the application starts accepting requests.
 */
object MigrationRunner {

  def runAll(): ZIO[RuntimeConfig & DatasourcePort, Throwable, Unit] =
    for {
      config <- ZIO.service[RuntimeConfig]
      _      <- ZIO.logInfo("Starting migration and initialization...")
      _      <- runDatabaseMigrations()
      _      <- if (config.fixtures.loadOnStartup) loadDatabaseFixtures() else ZIO.unit
      _      <- initializeVectorStore()
      _      <- initializeBlobStore()
      _      <- ZIO.logInfo("All migrations and initializations completed successfully")
    } yield ()

  private def runDatabaseMigrations(): ZIO[DatasourcePort, Throwable, Unit] =
    for {
      datasource      <- ZIO.service[DatasourcePort]
      quillDatasource <- ZIO.fromEither(
                           datasource match {
                             case qd: QuillDatasource => Right(qd)
                             case _                   => Left(new IllegalStateException("DatasourcePort is not a QuillDatasource"))
                           }
                         )
      migrationService = new FlywayMigrationService(quillDatasource.dataSource)
      _               <- migrationService.runMigrations()
    } yield ()

  private def loadDatabaseFixtures(): ZIO[RuntimeConfig, Throwable, Unit] =
    for {
      config        <- ZIO.service[RuntimeConfig]
      dbConfig       = config.adapters.driven.database
      fixtureService = new com.cyrelis.srag.infrastructure.fixtures.DatabaseFixtureService(dbConfig)
      hasData       <- fixtureService.hasData()
      _             <- if (hasData) {
             ZIO.logInfo("Database already contains data, skipping fixtures")
           } else {
             ZIO.logInfo("Database is empty, loading fixtures") *>
               fixtureService.loadFixtures()
           }
    } yield ()

  private def initializeVectorStore(): ZIO[RuntimeConfig, Throwable, Unit] =
    for {
      config      <- ZIO.service[RuntimeConfig]
      vectorConfig = config.adapters.driven.vectorStore
      initializer <- ZIO.succeed(createVectorStoreInitializer(vectorConfig))
      _           <- initializer.initialize()
    } yield ()

  private def initializeBlobStore(): ZIO[RuntimeConfig, Throwable, Unit] =
    for {
      config      <- ZIO.service[RuntimeConfig]
      blobConfig   = config.adapters.driven.blobStore
      initializer <- ZIO.succeed(createBlobStoreInitializer(blobConfig))
      _           <- initializer.initialize()
    } yield ()

  private def createVectorStoreInitializer(config: VectorStoreAdapterConfig): VectorStoreInitializer =
    config match {
      case cfg: VectorStoreAdapterConfig.Qdrant => new VectorStoreInitializer.QdrantInitializer(cfg)
    }

  private def createBlobStoreInitializer(config: BlobStoreAdapterConfig): BlobStoreInitializer =
    config match {
      case cfg: BlobStoreAdapterConfig.MinIO => new BlobStoreInitializer.MinIOInitializer(cfg)
    }
}
