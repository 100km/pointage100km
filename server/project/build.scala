import sbt._
import Keys._
import sbtassembly.Plugin._
import AssemblyKeys._

object Steenwerck extends Build {

  lazy val typesafeRepo = "Typesafe repository (releases)" at "http://repo.typesafe.com/typesafe/releases/"

  lazy val akka =
    Seq(libraryDependencies ++= Seq("com.typesafe.akka" % "akka-actor" % "2.0",
				    "com.typesafe.akka" % "akka-slf4j" % "2.0",
				    "ch.qos.logback" % "logback-classic" % "1.0.9" % "compile"))

  lazy val assemble =
    assemblySettings ++
    Seq(jarName in assembly <<= name(n => ("../../../bin/" + n + ".jar").toString),
	test in assembly := {})

  lazy val scopt = Seq(libraryDependencies += "com.github.scopt" %% "scopt" % "1.1.3")

  lazy val json = Seq(libraryDependencies += "net.liftweb" %% "lift-json" % "2.4")

  lazy val jackcess =
    Seq(libraryDependencies += "com.healthmarketscience.jackcess" % "jackcess" % "1.2.9")

  lazy val common = Project.defaultSettings ++ assemble ++
    Seq(scalaVersion := "2.9.1",
	scalacOptions ++= Seq("-unchecked", "-deprecation"))

  lazy val root =
    Project("root", file(".")) aggregate(replicate, couchsync, wipe, canape, config, stats)

  lazy val stats =
    Project("stats", file("stats"), settings = common ++ akka ++ scopt) dependsOn(canape)

  lazy val replicate =
    Project("replicate", file("replicate"), settings = common ++ akka ++ scopt) dependsOn(canape, config, steenwerck)

  lazy val couchsync =
    Project("couchsync", file("couchsync"), settings = common ++ scopt) dependsOn(canape, steenwerck)

  lazy val loader =
    Project("loader", file("loader"), settings = common ++ akka ++ jackcess ++ scopt) dependsOn(canape)

  lazy val wipe = Project("wipe", file("wipe"), settings = common ++ akka ++ scopt) dependsOn(canape, config)

  lazy val canape = Project("canape", file("libs/canape"), settings = common)

  lazy val steenwerck = Project("steenwerck", file("libs/steenwerck"), settings = common ++ akka) dependsOn(canape)

  lazy val config = Project(id = "config", base = file("libs/config"), settings = common)
}
