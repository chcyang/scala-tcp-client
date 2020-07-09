package io.github.chc.tcp.scala

import java.util.concurrent.atomic.AtomicBoolean

import io.github.chc.tcp.scala.exception.{TcpAccessException, TcpTimeoutException}

import scala.concurrent.Future

trait ITcpConnection {

  /**
   * send command
   *
   * @param command command
   * @return String receive message
   * @throws TcpAccessException  TcpAccessException
   * @throws TcpTimeoutException TcpTimeoutException
   */
  @throws[TcpAccessException]
  @throws[TcpTimeoutException]
  def sendCommand(command: String): Future[String]


  def isNormal: AtomicBoolean


  def asNormal(): Unit


  def asAbnormal(): Unit


  def getConnectionId: String
}
