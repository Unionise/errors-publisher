import sbt.*

object Dependencies {
  object Versions {
    val PlayMailer = "10.1.0"
  }

  val playMailer: Seq[ModuleID] = Seq(
    "org.playframework" %% "play-mailer" % Versions.PlayMailer,
    "org.playframework" %% "play-mailer-guice" % Versions.PlayMailer
  )

  lazy val kafka = "org.apache.kafka" % "kafka-clients" % "1.0.0"
  lazy val jodaTime = "joda-time" % "joda-time" % "2.9.9"
}
