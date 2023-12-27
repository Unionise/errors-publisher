import Dependencies._

lazy val root = (project in file(".")).enablePlugins(PlayScala).
  settings(
    inThisBuild(List(
      organization := "com.kupal",
      scalaVersion := "2.13.0",
      version      := "1.1.4"
    )),
    name := "errors-publisher",
    scalaSource in Compile := baseDirectory.value / "src/main/scala",
    scalaSource in Test := baseDirectory.value / "src/test/scala",

    publishTo := Some(Resolver.file("file",  new File( "../errors-publisher-repository" ))),

    libraryDependencies += playMailer,
    libraryDependencies += guice,
    libraryDependencies += kafka
  )

