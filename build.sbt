import Dependencies._

lazy val root = (project in file(".")).enablePlugins(PlayScala).
  settings(
    inThisBuild(List(
      organization := "com.kupal",
      scalaVersion := "2.12.18",
      version      := "1.1.3"
    )),
    name := "errors-publisher",
    scalaSource in Compile := baseDirectory.value / "src/main/scala",
    scalaSource in Test := baseDirectory.value / "src/test/scala",

    publishTo := Some(Resolver.file("file",  new File( "../errors-publisher-repository" ))),

    libraryDependencies += playMailer,
    libraryDependencies += guice,
    libraryDependencies += kafka,
    libraryDependencies += scalaTest % Test
  )

