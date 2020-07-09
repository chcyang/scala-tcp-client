package io.github.chc.tcp.scala.util

import com.eed3si9n.expecty.Expecty

trait SpecAssertions {

  val expect = new Expecty(failEarly = false)
}
