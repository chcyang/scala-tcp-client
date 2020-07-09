package io.github.chc.tcp.scala

import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.stream.QueueOfferResult.Enqueued
import akka.stream.scaladsl.Tcp.OutgoingConnection
import akka.stream.scaladsl.{Flow, Framing, Keep, Sink, Source, Tcp}
import akka.stream.{OverflowStrategy, StreamDetachedException, StreamTcpException}
import akka.util.ByteString
import io.github.chc.tcp.scala.exception.{TcpAccessException, TcpIdleTimeoutException, TcpTimeoutException}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Random

class TcpConnection(
                     host: String,
                     port: Int,
                     connectTimeOut: Int,
                     readTimeOut: Int,
                     idleTimeOut: Int
                   )(implicit system: ActorSystem) extends ITcpConnection {

  private val LOGGER = LoggerFactory.getLogger(classOf[TcpConnection])

  private val isNormalFlag: AtomicBoolean = new AtomicBoolean(true)

  override def isNormal: AtomicBoolean = this.isNormalFlag

  override def asNormal(): Unit = {
    this.isNormalFlag.set(true)
  }

  override def asAbnormal(): Unit = {
    this.isNormalFlag.set(false)
  }

  private val uniqueKey: String = s"Tcp-Connection-${Random.alphanumeric.take(8).mkString}"
  private implicit val ec: ExecutionContextExecutor = system.dispatcher
  private val remoteAddress = InetSocketAddress.createUnresolved(host, port)
  private val connection: Flow[ByteString, ByteString, Future[OutgoingConnection]] = Tcp().outgoingConnection(
    remoteAddress,
    connectTimeout = connectTimeOut.seconds,
    idleTimeout = idleTimeOut.seconds
  )

  /**
   * declare Source.queue
   */
  val (in, out) = Source.queue[String](100, OverflowStrategy.backpressure)
    .map(ByteString(_))
    .via(connection)
    .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = false))
    .map(_.utf8String)
    .toMat(Sink.queue())(Keep.both)
    .run()

  /**
   * send command
   *
   * @param command command
   * @return String receive message
   * @throws TcpAccessException  TcpAccessException
   * @throws TcpTimeoutException TcpTimeoutException
   */
  def sendCommand(command: String): Future[String] = {
    if (LOGGER.isDebugEnabled) LOGGER.debug(s"Connection $uniqueKey used for message $command ")
    val body: String = if (command.endsWith("\n")) command else command.concat("\n")
    in.offer(body).flatMap {
      case Enqueued =>
        val delayed = akka.pattern.after(readTimeOut.seconds, using = system.scheduler)(Future.failed(new TcpTimeoutException("Read Timeout")))
        Future.firstCompletedOf(Seq(out.pull(), delayed))
          .map {
            case Some(res) => if (LOGGER.isDebugEnabled) LOGGER.debug("The response message : " + res.replace("\n", ""))
              res
            case None => if (LOGGER.isDebugEnabled) LOGGER.debug("The response message is empty.")
              throw new TcpAccessException("The response message is empty")
          }

      case other =>
        Future.failed(new IllegalStateException(s"Stream returned $other"))
    }.recover {
      case ate: TcpTimeoutException =>
        LOGGER.error("TCP connection error : socket read timeout. cause:", ate)
        asAbnormal()
        throw ate
      case sde: StreamDetachedException =>
        LOGGER.warn("TCP connection error : StreamDetachedException or connection idleTimeout. cause:", sde)
        asAbnormal()
        throw new TcpIdleTimeoutException(sde.getMessage, sde)
      case ie: IOException =>
        LOGGER.error("TCP connection error : command send error. cause:", ie)
        asAbnormal()
        throw new TcpAccessException("TCP connection error : command send error.", ie)
      case ste: StreamTcpException =>
        LOGGER.error("TCP connection error : server not available. cause:", ste)
        asAbnormal()
        throw new TcpAccessException(ste.getMessage, ste)
      case e: Exception =>
        LOGGER.error("TCP connection error : other error. cause:", e)
        asAbnormal()
        throw e
    }

  }

  /**
   * get unique key of connection
   */
  override def getConnectionId: String = this.uniqueKey

}
