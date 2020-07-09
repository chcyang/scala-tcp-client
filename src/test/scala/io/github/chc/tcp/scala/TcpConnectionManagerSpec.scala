package io.github.chc.tcp.scala

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.{Config, ConfigFactory}
import io.github.chc.tcp.scala.exception.TcpTimeoutException
import io.github.chc.tcp.scala.server.MockTcpServer
import io.github.chc.tcp.scala.util.{DISessionSupport, StandardSpec, UtilityDIDesign}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import wvlet.airframe.Design

import scala.concurrent.{ExecutionContextExecutor, Future, TimeoutException}

class TcpConnectionManagerSpec
  extends TestKit(ActorSystem("TcpConnectionManagerSpec"))
    with StandardSpec
    with ScalaFutures
    with DISessionSupport {

  implicit val ec: ExecutionContextExecutor = system.dispatcher
  override val diDesign: Design = UtilityDIDesign.utilityDesign
    .bind[Config].toInstance(ConfigFactory.load)
    .bind[TcpConnectionConfig].toSingleton
    .bind[ActorSystem].toInstance(system)

  val tcpServer = new MockTcpServer()
  tcpServer.startUp()

  val manager: TcpConnectionManager = diSession.build[TcpConnectionManager]

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Millis))

  behavior of "TcpConnectionManagerSpec"

  "Normal" should
    "Normal#sendCommand" in {
    val message: Future[String] = manager.sendCommand("31#345678901234")
    whenReady(message) { res =>
      expect {
        res === "Response-31#345678901234"
      }
    }
  }

  "Abnormal" should "SendTimeOut" in {
    val message: Future[String] = manager.sendCommand("S30#345678901234")
    whenReady(message.failed) { throwable =>
      expect {
        throwable.isInstanceOf[TimeoutException]
        throwable.getMessage === "Tcp command send timeout"
      }
    }
  }
  "Abnormal" should
    "SocketReadTimeOut" in {
    val diDesign: Design = UtilityDIDesign.utilityDesign
      .bind[Config].toInstance(ConfigFactory.load)
      .bind[TcpConnectionConfig].toSingleton
      .bind[ActorSystem].toInstance(system)
      .bind[Config].toInstance {
      ConfigFactory.parseString(
        s"""
           |io.github.chc.tcp.scala {
           |  sendTimeOut = 10
           |}
       """.stripMargin).withFallback(ConfigFactory.load())
    }

    val manager = diDesign.newSession.build[TcpConnectionManager]
    val message: Future[String] = manager.sendCommand("S30#345678901234")
    whenReady(message.failed) { throwable =>
      expect {
        throwable.isInstanceOf[TcpTimeoutException]
        throwable.getMessage === "java.lang.RuntimeException: Read Timeout"
      }
    }
  }


}
