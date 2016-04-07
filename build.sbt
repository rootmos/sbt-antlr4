sbtPlugin := true

name := "sbt-antlr4"

organization := "com.simplytyped"

scalaVersion := "2.10.5"

crossScalaVersions := Seq("2.11.7")

version := "0.7.10"

publishTo := Some {
  val target = if(version.value contains "SNAPSHOT") "snapshots" else "releases"
  Resolver.file("simplytyped.com", new File("/Users/ihji/Works/simplytyped.github.io/repo",target))
}
