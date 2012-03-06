import sbt._
import Keys._
import cc.spray.revolver.RevolverPlugin._
import sbtassembly.Plugin._
import AssemblyKeys._

object Steenwerck extends Build {

  lazy val akka =
    Seq(libraryDependencies ++= Seq("com.typesafe.akka" % "akka-actor" % "2.0-RC4",
				    "com.typesafe.akka" % "akka-slf4j" % "2.0-RC4",
				    "ch.qos.logback" % "logback-classic" % "1.0.0" % "compile"),
	resolvers += typesafeRepo)

  lazy val typesafeRepo = "Typesafe repository (releases)" at "http://repo.typesafe.com/typesafe/releases/"

  lazy val assemble =
    assemblySettings ++ Seq(jarName in assembly <<= jn,
                            test in assembly := {})

  lazy val scopt = Seq(libraryDependencies += "com.github.scopt" %% "scopt" % "1.1.3")

  lazy val json = Seq(libraryDependencies += "net.liftweb" %% "lift-json" % "2.4")

  lazy val jackcess =
    Seq(libraryDependencies += "com.healthmarketscience.jackcess" % "jackcess" % "1.2.6")

  lazy val root =
    Project("root", file(".")) aggregate(replicate, couchsync, wipe, canape, config, stats)

  lazy val stats =
    Project("stats", file("stats")) dependsOn(canape) settings(akka: _*) settings(Revolver.settings: _*) settings(assemble: _*)

  lazy val replicate =
    Project("replicate", file("replicate")) dependsOn(canape, config, steenwerck) settings(akka: _*) settings(scopt: _*) settings(Revolver.settings: _*) settings(assemble: _*)

  lazy val couchsync =
    Project("couchsync", file("couchsync")) dependsOn(canape, steenwerck) settings(scopt: _*) settings(Revolver.settings: _*) settings(assemble: _*)

  lazy val loader =
    Project("loader", file("loader")) dependsOn(canape) settings(akka: _*) settings(jackcess: _*) settings(scopt: _*) settings(Revolver.settings: _*) settings(assemble: _*)

  lazy val wipe = Project("wipe", file("wipe")) dependsOn(canape, config) settings(akka: _*) settings(scopt: _*) settings(Revolver.settings: _*) settings(assemble: _*)

  lazy val canape = Project("canape", file("libs/canape"))

  lazy val steenwerck = Project("steenwerck", file("libs/steenwerck")) dependsOn(canape) settings(akka: _*)

  lazy val config = Project(id = "config", base = file("libs/config"))

  // Used by subprojects to set the assembly JAR file
  lazy val jn = name { n => ("../../../bin/" + n + ".jar").toString }
}
