import sbt._
import Keys._
import sbtassembly.AssemblyPlugin.autoImport._

lazy val akka =
  Seq(libraryDependencies ++= Seq("com.typesafe.akka" %% "akka-actor" % "2.4.2",
                                  "com.typesafe.akka" %% "akka-slf4j" % "2.4.2",
                                  "com.typesafe.akka" %% "akka-stream" % "2.4.2",
                                  "com.typesafe.akka" %% "akka-stream-testkit" % "2.4.2" % "test",
                                  "com.typesafe.akka" %% "akka-http-core" % "2.4.2",
                                  "com.iheart" %% "ficus" % "1.2.2",
                                  "ch.qos.logback" % "logback-classic" % "1.1.6"))

lazy val defaultShellScript = Seq("#! /bin/sh", """exec java -jar "$0" "$@"""")

lazy val assemble =
  Seq(assemblyJarName in assembly := name.value,
      target in assembly := new File("../bin/"),
      assemblyOption in assembly := (assemblyOption in assembly).value.copy(prependShellScript = Some(defaultShellScript)),
      test in assembly := {})

lazy val scopt = Seq(libraryDependencies += "com.github.scopt" %% "scopt" % "3.4.0")

lazy val json = Seq(libraryDependencies += "com.typesafe.play" %% "play-json" % "2.5.0-RC2")

lazy val specs2 = Seq(libraryDependencies += "org.specs2" %% "specs2-core" % "3.7" % "test",
                      fork in Test := true)

lazy val csv = Seq(libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.3.0")

lazy val mysql =
  Seq(libraryDependencies ++= Seq("org.apache.commons" % "commons-dbcp2" % "2.1.1",
                                  "commons-dbutils" % "commons-dbutils" % "1.6",
                                  "mysql" % "mysql-connector-java" % "5.1.38"))

lazy val common = Project.defaultSettings ++ assemble ++
  Seq(scalaVersion := "2.11.7",
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      resolvers ++= Seq("Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/", Resolver.jcenterRepo))

lazy val pointage100km =
  Project("pointage100km", file(".")) aggregate(replicate, wipe, stats, loader)

lazy val stats =
  Project("stats", file("stats"), settings = common ++ akka ++ scopt) dependsOn(canape, steenwerck)

lazy val replicate =
  Project("replicate", file("replicate"), settings = common ++ akka ++ json ++ scopt ++ specs2 ++
    csv ++ Revolver.settings) dependsOn(canape, steenwerck, rxtelegram)

lazy val loader =
  Project("loader", file("loader"), settings = common ++ akka ++ json ++ mysql ++ scopt ++ Revolver.settings) dependsOn(canape, steenwerck)

lazy val wipe = Project("wipe", file("wipe"), settings = common ++ akka ++ scopt) dependsOn(canape, steenwerck)

lazy val canape = Project("canape", file("libs/canape"), settings = common)

lazy val steenwerck = Project("steenwerck", file("libs/steenwerck"), settings = common ++ json ++ akka) dependsOn(canape)

lazy val rxtelegram = Project("rxtelegram", file("libs/rxtelegram"), settings = common)
