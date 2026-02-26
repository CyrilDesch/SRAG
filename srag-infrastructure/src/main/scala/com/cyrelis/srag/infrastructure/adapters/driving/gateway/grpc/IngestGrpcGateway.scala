package com.cyrelis.srag.infrastructure.adapters.driving.gateway.grpc

import com.cyrelis.srag.application.usecases.ingestion.IngestService

final class IngestGrpcGateway(ingestPort: IngestService) {
  def description: String =
    s"gRPC gateway bound to ingest port: ${ingestPort.getClass.getSimpleName}"
}
