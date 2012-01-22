import sbt._
import Keys._

object Steenwerck extends Build {

  val dispatch = Seq("net.databinder" %% "dispatch-http" % "0.8.7" % "compile")

  val akka = Seq("com.typesafe.akka" % "akka-actor" % "2.0-M2",
		 "com.typesafe.akka" % "akka-slf4j" % "2.0-M2",
		 "ch.qos.logback" % "logback-classic" % "1.0.0" % "compile")

  lazy val root =
    Project("root", file(".")) aggregate(replicate, couchsync, wipe)

  lazy val replicate =
    Project("replicate", file("replicate")) dependsOn(canape, config) settings {
      libraryDependencies ++= dispatch ++ akka
    }

  lazy val couchsync =
    Project("couchsync", file("couchsync")) dependsOn(canape) settings {
      libraryDependencies ++= dispatch
    }

  lazy val wipe = Project("wipe", file("wipe")) dependsOn(canape, config) settings {
    libraryDependencies ++= dispatch
  }

  lazy val canape = Project("canape", file("libs/canape")) dependsOn(dispatchLiftJson)

  lazy val dispatchLiftJson = uri("git://github.com/dispatch/dispatch-lift-json#0.1.1")

  lazy val config = Project(id = "config", base = file("libs/config"))

  // Used by subprojects to set the proguard JAR file
  lazy val mjp = (baseDirectory in root, name) { (b, n) => b / "bin" / (n + ".jar") }

}
