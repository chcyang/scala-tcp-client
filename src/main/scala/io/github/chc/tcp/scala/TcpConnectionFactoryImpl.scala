package io.github.chc.tcp.scala

import akka.actor.ActorSystem

class TcpConnectionFactoryImpl(config: TcpConnectionConfig)(implicit val system: ActorSystem) extends ITcpConnectionFactory {


  override def createTcpConnection(tcpConnectionConfig: TcpConnectionConfig): ITcpConnection = new TcpConnection(
    config.hostName,
    config.portNo,
    config.connectTimeOut,
    config.readTimeOut,
    config.idleTimeOut
  )
}
