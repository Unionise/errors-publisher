import sbt._

object Dependencies {
  lazy val playMailer = "com.typesafe.play" %% "play-mailer" % "8.0.1"
  lazy val kafka = "org.apache.kafka" % "kafka-clients" % "1.0.0"
}
