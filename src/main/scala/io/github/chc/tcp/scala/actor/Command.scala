package io.github.chc.tcp.scala.actor

import io.github.chc.tcp.scala.ITcpConnection

import scala.concurrent.Promise

sealed trait Command

final case class SendCommand(request: String, responsePromise: Promise[String]) extends Command

sealed trait Response

final case class SuccessResponse(msg: String) extends Response

final case class FailureResponse(throwable: Throwable) extends Response

sealed trait Result

final case class ResponseReceived(response: String, responsePromise: Promise[String], connection: ITcpConnection) extends Result

final case class ResponseFailed(exception: Exception, responsePromise: Promise[String], connection: ITcpConnection) extends Result

final case class IdleTimeoutFailed(requst: String, responsePromise: Promise[String], connection: ITcpConnection) extends Result

final case class PromiseAlreadyCompleted(connection: ITcpConnection) extends Result

final case class ConnectionFreed()
