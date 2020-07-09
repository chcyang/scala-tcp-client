package io.github.chc.tcp.scala

import akka.actor.{ActorSystem, Scheduler}
import akka.pattern.after
import io.github.chc.tcp.scala.actor._
import io.github.chc.tcp.scala.exception.TcpTimeoutException
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise, TimeoutException}

class TcpConnectionManager(tcpConnectionFactory: TcpConnectionFactoryImpl,
                           tcpConfig: TcpConnectionConfig)(implicit system: ActorSystem) {

  import system.dispatcher

  private val LOGGER = LoggerFactory.getLogger(classOf[TcpConnectionManager])


  private[this] val managerActor = system.actorOf(TcpConnectionManagerActor.props(tcpConnectionFactory, tcpConfig))

  private[this] var retryCtnTmp: Int = 0

  private def sendCommandWithRetry(command: String): Future[Response] = {

    def send(): Future[Response] = {
      val responsePromise: Promise[String] = Promise()
      if (LOGGER.isDebugEnabled()) LOGGER.debug("Send msg:{} to actor!", command)
      managerActor ! SendCommand(command, responsePromise)
      Future.firstCompletedOf(Seq(
        responsePromise.future,
        after(
          tcpConfig.sendTimeOut.seconds,
          system.scheduler)(Future.failed(new TimeoutException("Tcp command send timeout")))
      )).recover {
        // retry when timeout
        case te: TcpTimeoutException =>
          responsePromise.tryFailure(te)
          throw te
        case timeoutException: TimeoutException =>
          responsePromise.tryFailure(timeoutException)
          throw timeoutException
        case e: Exception => FailureResponse(e)
      }.map {
        case res: String => SuccessResponse(res)
        case FailureResponse(e) => FailureResponse(e)
      }
    }

    implicit val scheduler: Scheduler = system.scheduler
    akka.pattern.retry(
      () => send(),
      attempts = tcpConfig.retryCnt,
      delay = tcpConfig.retryWaitTime.milliseconds)

  }

  def sendCommand(command: String): Future[String] = {
    sendCommandWithRetry(command)
      .map {
        case SuccessResponse(msg) => msg
        case FailureResponse(e) => throw e
      }
  }
}
