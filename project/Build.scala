import sbt._
import Keys._

import spray.revolver.RevolverPlugin._

object AitkBuild extends Build {
  val slf4j = "org.slf4j" % "slf4j-api" % "1.7.6"
  val logbackVersion = "1.1.1"
  val logbackCore = "ch.qos.logback" % "logback-core" % logbackVersion
  val logbackClassic = "ch.qos.logback" % "logback-classic" % logbackVersion
  val loggingImplementations = Seq(logbackCore, logbackClassic)

  val allenAiCommon = "org.allenai.common" %% "common" % "0.0.1-SNAPSHOT"
  val allenAiTestkit = "org.allenai.common" %% "testkit" % "0.0.2-SNAPSHOT"

  val clearGroup = "com.clearnlp"
  val clearVersion = "2.0.0"
  val clear = clearGroup % "clearnlp" % clearVersion
  val opennlp = "org.apache.opennlp" % "opennlp-tools" % "1.5.3" exclude("net.sf.jwordnet", "jwnl")
  val chalk = "org.scalanlp" % "chalk" % "1.3.0" exclude("com.typesafe.akka", "akka-actor_2.10") exclude("org.apache.logging.log4j", "log4j-api")

  val testingLibraries = Seq(allenAiTestkit % "test")

  val scopt = "com.github.scopt" %% "scopt" % "3.2.0"

  val sprayVersion = "1.3.1"
  val akkaVersion = "2.3.2"

  val apache2 = "Apache 2.0 " -> url("http://www.opensource.org/licenses/bsd-3-clause")

  lazy val root = Project(id = "aitk-root", base = file(".")).settings (
    publish := { },
    publishTo := Some("bogus" at "http://nowhere.com"),
    publishLocal := { }
  ).aggregate(tools, viz)

  val buildSettings = Defaults.defaultSettings ++ Revolver.settings ++
    Seq(
      organization := "org.allenai.aitk",
      scalaVersion := "2.10.4",
      scalacOptions ++= Seq("-Xlint", "-deprecation", "-feature"),
      conflictManager := ConflictManager.strict,
      resolvers ++= Seq(
              "AllenAI Snapshots" at "http://utility.allenai.org:8081/nexus/content/repositories/snapshots",
              "AllenAI Releases" at "http://utility.allenai.org:8081/nexus/content/repositories/releases",
              "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"),
      libraryDependencies ++= testingLibraries,
      dependencyOverrides ++= Set(
        "org.scala-lang" % "scala-library" % scalaVersion.value)
    )

  lazy val tools = Project(
    id = "tools-root",
    base = file("tools")).aggregate(lemmatize, tokenize, postag, chunk)

  lazy val viz = Project(
    id = "viz",
    base = file("viz"),
    settings = buildSettings) dependsOn(toolsCore)

  lazy val toolsCore = Project(
    id = "tools-core",
    base = file("tools/core"),
    settings = buildSettings)

  lazy val lemmatize = Project(
    id = "lemmatize",
    base = file("tools/lemmatize"),
    settings = buildSettings ++ Seq(
      name := "aitk-lemmatize",
      licenses := Seq(
        "Academic License (for original lex files)" -> url("http://www.informatics.sussex.ac.uk/research/groups/nlp/carroll/morph.tar.gz"),
        "Apache 2.0 (for supplemental code)" -> url("http://www.opensource.org/licenses/bsd-3-clause")),
      libraryDependencies ++= Seq(clear,
        "edu.washington.cs.knowitall" % "morpha-stemmer" % "1.0.5"))
  ) dependsOn(toolsCore)

  lazy val tokenize = Project(
    id = "tokenize",
    base = file("tools/tokenize"),
    settings = buildSettings ++ Seq(
      name := "aitk-tokenize",
      licenses := Seq(apache2),
      libraryDependencies ++= Seq(chalk))
  ) dependsOn(toolsCore)

  lazy val postag = Project(
    id = "postag",
    base = file("tools/postag"),
    settings = buildSettings ++ Seq(
      name := "aitk-postag",
      licenses := Seq(apache2),
      libraryDependencies ++= Seq(opennlp, "edu.washington.cs.knowitall" % "opennlp-postag-models" % "1.5" ))
  ) dependsOn(tokenize)

  lazy val chunk = Project(
    id = "chunk",
    base = file("tools/chunk"),
    settings = buildSettings ++ Seq(
      name := "aitk-chunk",
      licenses := Seq(apache2),
      libraryDependencies ++= Seq(opennlp, "edu.washington.cs.knowitall" % "opennlp-chunk-models" % "1.5" ))
  ) dependsOn(postag)
}