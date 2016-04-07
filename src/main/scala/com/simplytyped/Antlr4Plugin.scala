package com.simplytyped

import sbt._
import Keys._
import scala.collection._

object Antlr4Plugin extends Plugin {
  val Antlr4 = config("antlr4")

  val antlr4Version = SettingKey[String]("Version of antlr4")
  val antlr4Generate = TaskKey[Seq[File]]("Generate classes from antlr4 grammars")
  val antlr4RuntimeDependency = SettingKey[ModuleID]("Library dependency for antlr4 runtime")
  val antlr4Dependency = SettingKey[ModuleID]("Build dependency required for parsing grammars")
  val antlr4PackageName = SettingKey[Option[String]]("Name of the package for generated classes")
  val antlr4GenListener = SettingKey[Boolean]("Generate listener")
  val antlr4GenVisitor = SettingKey[Boolean]("Generate visitor")

  private val antlr4BuildDependency = SettingKey[ModuleID]("Build dependency required for parsing grammars, scoped to plugin")

  def antlr4GeneratorTask : Def.Initialize[Task[Seq[File]]] = Def.task {
    val cachedCompile = FileFunction.cached(streams.value.cacheDirectory / "antlr4", FilesInfo.lastModified, FilesInfo.exists) {
      in : Set[File] =>
        runAntlr(
          srcFiles = in,
          targetBaseDir = (javaSource in Antlr4).value,
          classpath = (managedClasspath in Antlr4).value.files,
          log = streams.value.log,
          maybePackageName = (antlr4PackageName in Antlr4).value,
          listenerOpt = (antlr4GenListener in Antlr4).value,
          visitorOpt = (antlr4GenVisitor in Antlr4).value
        )
    }
    cachedCompile(((sourceDirectory in Antlr4).value ** "*.g4").get.toSet).toSeq
  }

  def runAntlr(
      srcFiles: Set[File],
      targetBaseDir: File,
      classpath: Seq[File],
      log: Logger,
      maybePackageName: Option[String],
      listenerOpt: Boolean,
      visitorOpt: Boolean) = {
    var baseArgs = List("-cp", Path.makeString(classpath), "org.antlr.v4.Tool")
    baseArgs ++= { if(listenerOpt) Seq("-listener") else Seq("-no-listener") }
    baseArgs ++= { if(visitorOpt) Seq("-visitor") else Seq("-no-visitor") }

    case class Package(name: String) {
      val files = mutable.ListBuffer.empty[File]
      val targetDir = name.split('.').foldLeft(targetBaseDir){_/_}
      def args = Seq("-package", name, "-o", targetDir.toString) ++ files.map(_.toString)
    }
    val packages = mutable.Map.empty[String, Package]

    for(file <- srcFiles) {
      val packageName = maybePackageName.getOrElse(derivePackageName(file))
      packages.getOrElseUpdate(packageName, Package(packageName)).files += file
    }

    for((_, p) <- packages) {
      val exitCode = Process("java", baseArgs ++ p.args) ! log
      if(exitCode != 0) sys.error(s"Antlr4 failed with exit code $exitCode")
    }

    (targetBaseDir ** "*.java").get.toSet
  }

  private def derivePackageName(file: File): String = {
    val dirname = file.absolutePath.reverse.dropWhile(_ != '/').drop(1).reverse
    dirname.split('/').reverse.takeWhile(_ != "antlr4").reverse.reduce(_ ++ "." ++ _)
  }

  val antlr4Settings = inConfig(Antlr4)(Seq(
    sourceDirectory <<= (sourceDirectory in Compile) {_ / "antlr4"},
    javaSource <<= (sourceManaged in Compile).apply(_ / "antlr4"),
    managedClasspath <<= (configuration, classpathTypes, update) map Classpaths.managedJars,
    antlr4Version := "4.5",
    antlr4Generate <<= antlr4GeneratorTask,
    antlr4Dependency := "org.antlr" % "antlr4" % antlr4Version.value,
    antlr4RuntimeDependency := "org.antlr" % "antlr4-runtime" % antlr4Version.value,
    antlr4BuildDependency := antlr4Dependency.value % Antlr4.name,
    antlr4PackageName := None,
    antlr4GenListener := true,
    antlr4GenVisitor := false
  )) ++ Seq(
    ivyConfigurations += Antlr4,
    managedSourceDirectories in Compile <+= (javaSource in Antlr4),
    sourceGenerators in Compile <+= (antlr4Generate in Antlr4),
    watchSources <++= sourceDirectory map {path => (path ** "*.g4").get},
    cleanFiles <+= (javaSource in Antlr4),
    libraryDependencies <+= (antlr4BuildDependency in Antlr4),
    libraryDependencies <+= (antlr4RuntimeDependency in Antlr4)
  )
}
