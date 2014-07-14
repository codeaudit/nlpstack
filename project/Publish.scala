import sbt._
import Keys._

import scala.util.Try

object Publish {
  val nexusHost = "utility.allenai.org"
  val nexus = s"http://${nexusHost}:8081/nexus/content/repositories/"

  lazy val settings = Seq(
    publishTo := {
      if(isSnapshot.value)
        Some("snapshots" at nexus + "snapshots")
      else
        Some("releases"  at nexus + "releases")
    })
}
