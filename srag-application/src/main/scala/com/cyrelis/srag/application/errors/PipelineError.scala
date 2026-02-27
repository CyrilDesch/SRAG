package com.cyrelis.srag.application.errors

sealed trait PipelineError {
  def message: String
  def cause: Option[Throwable] = None
}

object PipelineError {
  final case class TranscriptionError(
    message: String,
    override val cause: Option[Throwable] = None
  ) extends PipelineError

  final case class EmbeddingError(
    message: String,
    override val cause: Option[Throwable] = None
  ) extends PipelineError

  final case class DatabaseError(
    message: String,
    override val cause: Option[Throwable] = None
  ) extends PipelineError

  final case class VectorStoreError(
    message: String,
    override val cause: Option[Throwable] = None
  ) extends PipelineError

  final case class LexicalStoreError(
    message: String,
    override val cause: Option[Throwable] = None
  ) extends PipelineError

  final case class BlobStoreError(
    message: String,
    override val cause: Option[Throwable] = None
  ) extends PipelineError

  final case class QueueError(
    message: String,
    override val cause: Option[Throwable] = None
  ) extends PipelineError

  final case class RerankerError(
    message: String,
    override val cause: Option[Throwable] = None
  ) extends PipelineError

  final case class TimeoutError(
    operation: String,
    timeoutMs: Long,
    override val cause: Option[Throwable] = None
  ) extends PipelineError {
    override def message: String = s"Operation '$operation' timed out after ${timeoutMs}ms"
  }

  final case class ConfigurationError(
    message: String,
    override val cause: Option[Throwable] = None
  ) extends PipelineError
}
