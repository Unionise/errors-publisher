import Dependencies._

lazy val root = (project in file(".")).enablePlugins(PlayScala).
  settings(
    inThisBuild(List(
      organization := "com.kupal",
      scalaVersion := "2.13.18",
      version      := "1.1.5"
    )),
    name := "errors-publisher",
    Compile / scalaSource := baseDirectory.value / "src/main/scala",
    Test / scalaSource := baseDirectory.value / "src/test/scala",

    publishTo := Some(Resolver.file("file",  new File( "../errors-publisher-repository" ))),

    libraryDependencies ++= playMailer,
    libraryDependencies += guice,
    libraryDependencies += kafka,
    libraryDependencies += jodaTime
  )

