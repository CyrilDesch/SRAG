package com.cyrelis.srag.application.testsupport

import com.cyrelis.srag.application.errors.PipelineError
import zio.*

object SpecSupport {
  type AppTask[A] = ZIO[Any, PipelineError, A]

  def unexpectedPipelineCall[A](method: String): AppTask[A] =
    ZIO.dieMessage(s"Unexpected test call to $method")

  def unexpectedTaskCall[A](method: String): Task[A] =
    ZIO.dieMessage(s"Unexpected test call to $method")
}
