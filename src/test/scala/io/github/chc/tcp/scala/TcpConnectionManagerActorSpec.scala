package io.github.chc.tcp.scala

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestKit
import com.typesafe.config.{Config, ConfigFactory}
import io.github.chc.tcp.scala.actor.{SendCommand, TcpConnectionManagerActor}
import io.github.chc.tcp.scala.exception.{TcpAccessException, TcpIdleTimeoutException, TcpTimeoutException}
import io.github.chc.tcp.scala.util.{DISessionSupport, StandardSpec, UtilityDIDesign}
import org.scalatest.Inside
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.slf4j.LoggerFactory
import wvlet.airframe.Design

import scala.concurrent.{ExecutionContextExecutor, Future, Promise}
import scala.util.Random


class TcpConnectionMock(implicit system: ActorSystem) extends ITcpConnection {

  import system.dispatcher

  private val LOGGER = LoggerFactory.getLogger(classOf[TcpConnectionMock])

  private val uniqueKey: String = s"Tcp-Connection-${Random.alphanumeric.take(8).mkString}"

  /**
   * send command
   *
   * @param command command
   * @return String receive message
   * @throws TcpAccessException  TcpAccessException
   * @throws TcpTimeoutException TcpTimeoutException
   */
  override def sendCommand(command: String): Future[String] = {
    if (LOGGER.isDebugEnabled) LOGGER.debug(s"Connection $uniqueKey used for message $command ")
    command match {
      case "ReadTimeOut" =>
        asAbnormal()
        Future.failed(new TcpTimeoutException("Read Timeout"))
      case "AccessError" =>
        asAbnormal()
        Future.failed(new TcpAccessException("Access Error"))
      case "IdleTimeoutError" =>
        asAbnormal()
        if (Random.nextInt(10) >= 5) Future.failed(new TcpIdleTimeoutException("ConnectionIdleTimeoutException"))
        else Future("test-result")
      case "OtherError" =>
        asAbnormal()
        Future.failed(new Exception("Access Error"))
      case _ => Future("test-result")
    }
  }

  private val isNormalFlag: AtomicBoolean = new AtomicBoolean(true)

  override def isNormal: AtomicBoolean = this.isNormalFlag

  override def asNormal(): Unit = this.isNormalFlag.set(true)

  override def asAbnormal(): Unit = this.isNormalFlag.set(false)

  override def getConnectionId: String = this.uniqueKey
}

class TcpFactoryMock(implicit system: ActorSystem) extends ITcpConnectionFactory {

  private var newConnections: Int = 0

  def getConnectionNum: Int = this.newConnections

  /**
   * create Connection
   *
   * @param tcpConfig Config
   * @return TcpConnection
   */
  override def createTcpConnection(tcpConfig: TcpConnectionConfig): ITcpConnection = {
    val connection = new TcpConnectionMock()
    this.newConnections += 1
    connection
  }
}

class TcpConnectionManagerActorSpec extends TestKit(ActorSystem("TcpConnectionManagerActorSpec"))
  with StandardSpec
  with ScalaFutures
  with Inside
  with DISessionSupport {


  implicit val ec: ExecutionContextExecutor = system.dispatcher

  override val diDesign: Design = UtilityDIDesign.utilityDesign
    .bind[ITcpConnection].to[TcpConnectionMock]
    .bind[Config].toInstance(ConfigFactory.load)
    .bind[TcpConnectionConfig].toSingleton
    .bind[ActorSystem].toInstance(system)
    .bind[ITcpConnectionFactory].to[TcpFactoryMock]
    .bind[Config].toInstance {
    ConfigFactory.parseString(
      s"""
         |io.github.chc.tcp.scala {
         |  initPoolSize = 1
         |}
       """.stripMargin).withFallback(ConfigFactory.load())
  }

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(35, Seconds), interval = Span(100, Millis))
  val config: TcpConnectionConfig = diSession.build[TcpConnectionConfig]

  def creatActor(tcpConnectionFactory: ITcpConnectionFactory): ActorRef = {
    system.actorOf(
      Props(new TcpConnectionManagerActor(tcpConnectionFactory, config)))
  }


  behavior of "TcpConnectionManagerSpec"

  "Normal" should
    "create ManagerActor then initialConnection" in {
    diSession.withChildSession() { session =>
      val tcpConnectionFactory = session.build[TcpFactoryMock]
      val actor: ActorRef = creatActor(tcpConnectionFactory)
      Thread.sleep(1000)
      expect {
        tcpConnectionFactory.getConnectionNum === 1
      }
      system.stop(actor)
    }
  }

  "Normal" should "Normal response" in {
    diSession.withChildSession() { session =>
      val tcpConnectionFactory = session.build[TcpFactoryMock]
      val actor: ActorRef = creatActor(tcpConnectionFactory)
      val command = "test-command"
      val responsePromise: Promise[String] = Promise()
      actor ! SendCommand(command, responsePromise)
      whenReady(responsePromise.future) { res =>
        expect {
          res === "test-result"
          tcpConnectionFactory.getConnectionNum === 2
        }
      }
      system.stop(actor)
    }
  }


  "Normal" should "Request until busy" in {
    diSession.withChildSession() { session =>
      val tcpConnectionFactory = session.build[TcpFactoryMock]
      val actor: ActorRef = creatActor(tcpConnectionFactory)

      val command = "test-command"
      val tasks: Seq[Future[String]] = for (i <- 1 to 10) yield {
        val responsePromise: Promise[String] = Promise()
        actor ! SendCommand(command, responsePromise)
        responsePromise.future
      }
      val aggregated: Future[Seq[String]] = Future.sequence(tasks)
      whenReady(aggregated) {
        seqs =>
          seqs.foreach { res =>
            expect {
              res === "test-result"
              //until the max size of pool
              tcpConnectionFactory.getConnectionNum === 3
            }
          }
      }
      system.stop(actor)
    }
  }

  "Normal" should "Connection close by exception will have a new connection to replace it" in {
    diSession.withChildSession() { session =>
      val tcpConnectionFactory = session.build[TcpFactoryMock]
      val actor: ActorRef = creatActor(tcpConnectionFactory)
      val command = "AccessError"
      val responsePromise: Promise[String] = Promise()
      actor ! SendCommand(command, responsePromise)
      whenReady(responsePromise.future.failed) { throwable =>
        Thread.sleep(1000)
        expect {
          throwable.isInstanceOf[TcpAccessException]
          //until the max size of pool
          tcpConnectionFactory.getConnectionNum === 3
        }
      }
      system.stop(actor)
    }
  }

  "Normal" should "A request already failed by time out, responsePromise will return immediately without process" in {
    diSession.withChildSession() { session =>
      val tcpConnectionFactory = session.build[TcpFactoryMock]
      val actor: ActorRef = creatActor(tcpConnectionFactory)
      val command = "test-command"
      val responsePromise: Promise[String] = Promise()
      actor ! SendCommand(command, responsePromise)
      responsePromise.tryFailure(new TcpTimeoutException("Timeout"))
      Thread.sleep(1000)
      whenReady(responsePromise.future.failed) {
        res =>
          expect {
            res.isInstanceOf[TcpTimeoutException]
            tcpConnectionFactory.getConnectionNum === 2
          }
      }
      system.stop(actor)
    }
  }

  "Normal" should "TcpIdleTimeoutException" in {
    diSession.withChildSession() { session =>

      val tcpConnectionFactory = session.build[TcpFactoryMock]
      val actor: ActorRef = creatActor(tcpConnectionFactory)
      val command = "IdleTimeoutError"
      val responsePromise: Promise[String] = Promise()
      actor ! SendCommand(command, responsePromise)
      whenReady(responsePromise.future) { res =>
        expect {
          res === "test-result"
        }
      }
      system.stop(actor)
    }
  }


  "Normal" should "TcpAccessException" in {
    diSession.withChildSession() { session =>
      val tcpConnectionFactory = session.build[TcpFactoryMock]
      val actor: ActorRef = creatActor(tcpConnectionFactory)
      val command = "AccessError"
      val responsePromise: Promise[String] = Promise()
      actor ! SendCommand(command, responsePromise)
      whenReady(responsePromise.future.failed) { throwable =>
        expect {
          throwable.isInstanceOf[TcpAccessException]
        }
      }
      system.stop(actor)
    }
  }

  "Normal" should "OtherException#AsAccessException" in {
    diSession.withChildSession() { session =>
      val tcpConnectionFactory = session.build[TcpFactoryMock]
      val actor: ActorRef = creatActor(tcpConnectionFactory)
      val command = "OtherError"
      val responsePromise: Promise[String] = Promise()
      actor ! SendCommand(command, responsePromise)
      whenReady(responsePromise.future.failed) { throwable =>
        expect {
          throwable.isInstanceOf[Exception]
        }
      }
      system.stop(actor)
    }
  }

  "Normal" should "TcpTimeOutException" in {
    diSession.withChildSession() { session =>
      val tcpConnectionFactory = session.build[TcpFactoryMock]
      val actor: ActorRef = creatActor(tcpConnectionFactory)
      val command = "ReadTimeOut"
      val responsePromise: Promise[String] = Promise()
      actor ! SendCommand(command, responsePromise)
      whenReady(responsePromise.future.failed) { throwable =>
        expect {
          throwable.isInstanceOf[TcpTimeoutException]
        }
      }
      system.stop(actor)
    }
  }

}
