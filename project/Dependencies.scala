import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.4"
  lazy val playMailer = "com.typesafe.play" %% "play-mailer" % "6.0.0"
  lazy val kafka = "org.apache.kafka" % "kafka-clients" % "1.0.0"
}
