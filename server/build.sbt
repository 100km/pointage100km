import sbt._
import Keys._
import sbtassembly.AssemblyPlugin.autoImport._

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

lazy val akka =
  Seq(libraryDependencies ++= Seq("com.typesafe.akka" %% "akka-actor" % "2.3.9",
                                  "com.typesafe.akka" %% "akka-slf4j" % "2.3.9",
                                  "com.typesafe.akka" %% "akka-stream-experimental" % "1.0-M5",
                                  "net.ceedubs" %% "ficus" % "1.1.2",
                                  "ch.qos.logback" % "logback-classic" % "1.0.9"))

lazy val defaultShellScript = Seq("#! /bin/sh", """exec java -jar "$0" "$@"""")

lazy val assemble =
  Seq(assemblyJarName in assembly := name.value,
      target in assembly := new File("../bin/"),
      assemblyOption in assembly := (assemblyOption in assembly).value.copy(prependShellScript = Some(defaultShellScript)),
      test in assembly := {})

lazy val scopt = Seq(libraryDependencies += "com.github.scopt" %% "scopt" % "3.3.0")

lazy val json = Seq(libraryDependencies += "com.typesafe.play" %% "play-json" % "2.3.8")

lazy val specs2 = Seq(libraryDependencies += "org.specs2" %% "specs2-core" % "2.4.15" % "test",
                      fork in Test := true)

lazy val mysql =
  Seq(libraryDependencies ++= Seq("commons-dbcp" % "commons-dbcp" % "1.4",
                                  "commons-dbutils" % "commons-dbutils" % "1.5",
                                  "mysql" % "mysql-connector-java" % "5.1.22"))

lazy val common = Project.defaultSettings ++ assemble ++
  Seq(scalaVersion := "2.11.6",
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"))

lazy val root =
  Project("root", file(".")) aggregate(replicate, wipe, stats, loader)

lazy val stats =
  Project("stats", file("stats"), settings = common ++ akka ++ scopt) dependsOn(canape, steenwerck)

lazy val replicate =
  Project("replicate", file("replicate"), settings = common ++ akka ++ json ++ scopt ++ specs2) dependsOn(canape, config, steenwerck)

lazy val loader =
  Project("loader", file("loader"), settings = common ++ akka ++ json ++ mysql ++ scopt) dependsOn(canape)

lazy val wipe = Project("wipe", file("wipe"), settings = common ++ akka ++ scopt) dependsOn(canape, config)

lazy val canape = Project("canape", file("libs/canape"), settings = common)

lazy val steenwerck = Project("steenwerck", file("libs/steenwerck"), settings = common ++ json ++ akka) dependsOn(canape)

lazy val config = Project(id = "config", base = file("libs/config"), settings = common)
