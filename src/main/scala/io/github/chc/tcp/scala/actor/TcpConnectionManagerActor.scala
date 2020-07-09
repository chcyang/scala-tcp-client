package io.github.chc.tcp.scala.actor

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.pipe
import io.github.chc.tcp.scala.exception.TcpIdleTimeoutException
import io.github.chc.tcp.scala.{ITcpConnection, ITcpConnectionFactory, TcpConnectionConfig}
import org.slf4j.LoggerFactory

import scala.collection.immutable.Queue
import scala.concurrent.Future


object TcpConnectionManagerActor {

  def props(tcpConnectionFactory: ITcpConnectionFactory,
            tcpConfig: TcpConnectionConfig): Props = Props(new TcpConnectionManagerActor(tcpConnectionFactory, tcpConfig))

}

class TcpConnectionManagerActor(tcpConnectionFactory: ITcpConnectionFactory,
                                tcpConfig: TcpConnectionConfig) extends Actor with ActorLogging {

  private val initialConnections: Int = tcpConfig.initPoolSize
  private val maxConnections: Int = tcpConfig.maxPoolSize


  private val LOGGER = LoggerFactory.getLogger(classOf[TcpConnectionManagerActor])


  var readyConnections: Seq[ITcpConnection] = Seq.tabulate(initialConnections)(_ => createNewConnection())

  var inUseConnections: Set[ITcpConnection] = Set()

  /**
   * Message for waiting process
   */
  var requests: Queue[SendCommand] = Queue()

  override def receive: Receive = free

  import context.dispatcher

  def free: Receive = handleResult.orElse {
    case request: SendCommand =>
      this.readyConnections.headOption match {
        case Some(connection) =>
          this.readyConnections = this.readyConnections.filterNot(_ == connection)
          dispatch(connection, request) pipeTo self
          if (this.readyConnections.isEmpty) {
            if (numberOfConnections < maxConnections) {
              // refill the connection has been used
              this.readyConnections = this.readyConnections :+ createNewConnection()
            } else {
              // if no idle connection,change to busy
              if (LOGGER.isDebugEnabled()) LOGGER.debug("All connection is in used,change status to busy!")
              context.become(busy)
            }
          }
        case None =>
          //if no initialed connections ,will create it first
          if (numberOfConnections < maxConnections) {
            dispatch(createNewConnection(), request) pipeTo self
          } else {
            // if no idle connection,change to busy
            this.requests = this.requests.enqueue(request)
            context.become(busy)
          }
      }

    case _: ConnectionFreed =>
    // do nothing
  }

  def busy: Receive = handleResult.orElse {
    case request: SendCommand =>
      // when all connection has been used,put request into waiting Queue
      this.requests = this.requests.enqueue(request)

    case _: ConnectionFreed if this.requests.isEmpty =>
      context.become(free)

    case _: ConnectionFreed if this.requests.nonEmpty =>
      this.readyConnections.headOption match {
        case Some(connection) =>
          this.readyConnections = this.readyConnections.filterNot(_ == connection)
          val (request, newRequests) = this.requests.dequeue
          this.requests = newRequests
          dispatch(connection, request) pipeTo self
        case None =>
          LOGGER.error("Status out of control,there should have one ready connection left.")
      }
  }

  def handleResult: Receive = {
    case ResponseReceived(response, responsePromise, connection) =>
      responsePromise.trySuccess(response)
      freeConnection(connection)
      self ! ConnectionFreed()

    case PromiseAlreadyCompleted(connection) =>
      freeConnection(connection)
      self ! ConnectionFreed()

    case ResponseFailed(ex, responsePromise, connection) =>
      responsePromise.tryFailure(ex)
      freeConnection(connection)
      self ! ConnectionFreed()

    case IdleTimeoutFailed(request, responsePromise, connection) =>
      freeConnection(connection)
      dispatch(createNewConnection(), SendCommand(request, responsePromise)) pipeTo self

  }

  def dispatch(connection: ITcpConnection, request: SendCommand): Future[Result] = {
    if (request.responsePromise.isCompleted) {
      // response when a command already failure by timeout before process
      Future.successful(PromiseAlreadyCompleted(connection))
    } else {
      this.inUseConnections += connection
      connection.sendCommand(request.request)
        .map(ResponseReceived(_, request.responsePromise, connection))
        .recover {
          //if IdleTimeout happened,retry it immediately
          case _: TcpIdleTimeoutException =>
            LOGGER.warn("tcp connection has been closed when IdleTimeout happened, will try it again soon.")
            IdleTimeoutFailed(request.request, request.responsePromise, connection)
          //if other exception,response as failure
          case e: Exception =>
            LOGGER.error("Exception happened when sending message to tcp, Ex:", e)
            ResponseFailed(e, request.responsePromise, connection)
        }
    }
  }

  def freeConnection(connection: ITcpConnection): Unit = {
    this.inUseConnections -= connection
    if (connection.isNormal.get()) {
      //return connection after process normal end
      this.readyConnections = connection +: this.readyConnections
    } else {
      //replace connection if it has ben stopped by exception
      if (LOGGER.isDebugEnabled) LOGGER.debug("Connection:{} is stopped by error,new connection will create to replace it!", connection.getConnectionId)
      this.readyConnections = this.readyConnections :+ createNewConnection()
      if (numberOfConnections > maxConnections) {
        LOGGER.warn("Lived connections was over maxConnections,please check!")
      }
    }
  }

  def numberOfConnections: Int = this.readyConnections.size + this.inUseConnections.size

  def createNewConnection(): ITcpConnection = {
    val connection: ITcpConnection = tcpConnectionFactory.createTcpConnection(tcpConfig)
    if (LOGGER.isDebugEnabled) LOGGER.debug("tcpConnectionManagerActor New tcp connection instance created! ID:{}", connection.getConnectionId)
    connection
  }
}
