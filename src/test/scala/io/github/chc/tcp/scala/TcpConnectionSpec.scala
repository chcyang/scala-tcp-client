package io.github.chc.tcp.scala

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import io.github.chc.tcp.scala.exception.{TcpAccessException, TcpIdleTimeoutException, TcpTimeoutException}
import io.github.chc.tcp.scala.server.MockTcpServer
import io.github.chc.tcp.scala.util.StandardSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.Future

class TcpConnectionSpec extends TestKit(ActorSystem("TcpConnectionSpec"))
  with StandardSpec
  with AnyFlatSpecLike
  with ScalaFutures {

  private val config: TcpConnectionConfig = new TcpConnectionConfig(ConfigFactory.load())
  val connection: ITcpConnection = new TcpConnection(config.hostName,
    config.portNo,
    config.connectTimeOut,
    config.readTimeOut,
    config.idleTimeOut)

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Millis))

  val tcpServer = new MockTcpServer()
  tcpServer.startUp()

  behavior of "TcpConnectionSpec Set"

  "Normal" should
    "Future.successful(Done)" in {
    val future: Future[String] = connection.sendCommand("test-command")
    whenReady(future) { result =>
      expect {
        result === "Response-test-command"
      }
    }
  }


  "Abnormal" should
    "TcpTimeOutException" in {
    val future: Future[String] = connection.sendCommand("S30#test-command")
    whenReady(future.failed) { throwable =>
      expect {
        throwable.isInstanceOf[TcpTimeoutException]
        throwable.getMessage === "java.lang.RuntimeException: Read Timeout"
      }
    }
  }


  "Abnormal" should "ConnectionRefused" in {
    val conRefused: ITcpConnection = new TcpConnection(
      config.hostName,
      1234,
      30,
      5,
      10,
    )
    val future: Future[String] = conRefused.sendCommand("<335##3##3#TEST_KEY_01#TEST_KEY_05#0BC1234567890091#F#F#>")
    whenReady(future.failed) { throwable =>
      expect {
        throwable.isInstanceOf[TcpAccessException]
      }
    }
  }

  "Abnormal" should "TcpIdleTimeoutException" in {
    val conRefused: ITcpConnection = new TcpConnection(
      config.hostName,
      config.portNo,
      config.connectTimeOut,
      config.readTimeOut,
      5
    )
    //init connection
    conRefused.sendCommand("<335##3##3#TEST_KEY_01#TEST_KEY_05#0BC1234567890091#F#F#>")
    Thread.sleep(6000)
    //request again
    val future2: Future[String] = conRefused.sendCommand("<335##3##3#TEST_KEY_01#TEST_KEY_05#0BC1234567890091#F#F#>")
    whenReady(future2.failed) { throwable =>
      expect {
        throwable.isInstanceOf[TcpIdleTimeoutException]
      }
    }
  }

}
