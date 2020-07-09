import sbtassembly.AssemblyKeys.{assembly, assemblyJarName}

name := "scala-tcp-client"

version := "0.1"

scalaVersion := "2.13.3"
val AkkaVersion = "2.6.9"
val scalaTestVersion = "3.1.4"
val expectyVerion = "0.11.0"
val airframeVersion = "20.10.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % AkkaVersion,
  "com.typesafe" % "config" % "1.4.0",
  "org.slf4j" % "slf4j-api" % "1.7.25",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.scalatest" %% "scalatest" % scalaTestVersion,
  "com.eed3si9n.expecty" %% "expecty" % expectyVerion,
  "org.wvlet.airframe" %% "airframe" % airframeVersion,
  "com.opencsv" % "opencsv" % "3.7"

)

excludeDependencies += "org.apache.logging.log4j" % "log4j-slf4j-impl"

scalacOptions += "-deprecation"

Project.inConfig(Test)(baseAssemblySettings)
assemblyJarName in(Test, assembly) := s"${name.value}-test-${version.value}.jar"

test in assembly := {}
