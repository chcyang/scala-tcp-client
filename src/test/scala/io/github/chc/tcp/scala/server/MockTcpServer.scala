package io.github.chc.tcp.scala.server

import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.stream.scaladsl.{Flow, Framing, Sink, Source, Tcp}
import akka.util.ByteString

import scala.concurrent.Future

class MockTcpServer(implicit val system: ActorSystem) {
  val host = "127.0.0.1"
  val port = 8888
  val connections: Source[IncomingConnection, Future[ServerBinding]] =
    Tcp().bind(host, port)

  def editResponse(receiveCommand: String): String = {

    s"Response-$receiveCommand"
  }

  def testAdapter(in: String): String = {
    val mark: String = in.split("#")(0)
    mark match {
      case "S30" => Thread.sleep(5000) //sleep 5s
      case _ =>
    }
    in
  }

  val tcpServer = connections
    .to(Sink.foreach { connection =>
      // server logic, parses incoming commands
      val commandParser = Flow[String].takeWhile(_ != "BYE")
        .map {
          command => println(command); command
        }
        .map(testAdapter)
        .map(editResponse)

      val serverLogic = Flow[ByteString]
        .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
        .map(_.utf8String)
        .via(commandParser)
        .map(_ + "\n")
        .map(ByteString(_))
        .map {
          res =>
            Thread.sleep(10)
            res
        }

      connection.handleWith(serverLogic)
    })

  def startUp(): Unit = {
    tcpServer.run()
  }

  def shutdown(): Unit = {
    system.terminate()
  }
}

object MockTcpServer extends App {

  implicit val actorSystem: ActorSystem = ActorSystem.apply("TcpServerActor")
  val tcpServer = new MockTcpServer()

  tcpServer.startUp()
}
