package com.cyrelis.srag.domain.error

import scala.util.control.NoStackTrace

sealed trait DomainError extends Exception with NoStackTrace with Product with Serializable

object DomainError:
  final case class InvalidInput(message: String) extends DomainError:
    override def getMessage: String = message
