package com.cyrelis.srag.infrastructure.runtime

import com.cyrelis.srag.infrastructure.adapters.driving.Gateway
import com.cyrelis.srag.infrastructure.config.{AdapterFactory, RuntimeConfig}
import zio.*

trait DrivingWiring {
  val gatewayLayer: ZLayer[RuntimeConfig, Nothing, Gateway] =
    ZLayer {
      for {
        config <- ZIO.service[RuntimeConfig]
        gateway = AdapterFactory.createGateway(config.adapters.driving.api)
      } yield gateway
    }
}

object DrivingModule extends DrivingWiring {
  type DrivingEnvironment = Gateway

  val live: ZLayer[RuntimeConfig, Nothing, DrivingEnvironment] = gatewayLayer
}
