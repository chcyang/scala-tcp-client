package io.github.chc.tcp.scala

import com.typesafe.config.Config

class TcpConnectionConfig(config: Config) {

  private val tcpConfig: Config = config.getConfig("io.github.chc.tcp.scala")

  def hostName: String = tcpConfig.getString("hostName")

  def portNo: Int = tcpConfig.getInt("portNo")

  def readTimeOut: Int = tcpConfig.getInt("readTimeOut")

  def connectTimeOut: Int = tcpConfig.getInt("connectTimeOut")

  def idleTimeOut: Int = tcpConfig.getInt("idleTimeout")

  def initPoolSize: Int = tcpConfig.getInt("initPoolSize")

  def maxPoolSize: Int = tcpConfig.getInt("maxPoolSize")

  def retryCnt: Int = tcpConfig.getInt("retryCnt")

  def retryWaitTime: Int = tcpConfig.getInt("retryWaitTime")

  def sendTimeOut: Int = tcpConfig.getInt("sendTimeOut")
}
