import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.kupal",
      scalaVersion := "2.12.4",
      version      := "1.0.0"
    )),
    name := "errors-publisher",
    publishTo := Some(Resolver.file("file",  new File( "../errors-publisher-repository" ))),
    libraryDependencies += scalaTest % Test
  )
