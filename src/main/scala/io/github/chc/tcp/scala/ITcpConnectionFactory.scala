package io.github.chc.tcp.scala

trait ITcpConnectionFactory {

  def createTcpConnection(config: TcpConnectionConfig): ITcpConnection

}
