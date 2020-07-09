package io.github.chc.tcp.scala.util

import org.scalatest.{BeforeAndAfterAll, Suite}
import wvlet.airframe.{Design, Session}

trait DISessionSupport extends BeforeAndAfterAll {
  this: Suite =>

  protected val diDesign: Design

  protected lazy val diSession: Session = diDesign.newSession

  override def afterAll(): Unit = {
    try super.afterAll()
    finally diSession.shutdown
  }
}
