package io.github.chc.tcp.scala.util

import wvlet.airframe.{Design, newDesign}

object UtilityDIDesign extends UtilityDIDesign

trait UtilityDIDesign {

  val utilityDesign: Design = newDesign

}
