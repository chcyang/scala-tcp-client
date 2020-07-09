package io.github.chc.tcp.scala.exception


/**
 *
 * @param ex RuntimeException
 */
class TcpAccessException(private val ex: RuntimeException) extends Exception(ex: RuntimeException) {

  def this(message: String) = this(new RuntimeException(message))

  def this(message: String, throwable: Throwable) = this(new RuntimeException(message, throwable))
}
