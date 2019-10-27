import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbt.Keys._
import sbt._
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.AssemblyPlugin.defaultShellScript

import scalariform.formatter.preferences._

lazy val akka =
  Seq(libraryDependencies ++= Seq("com.typesafe.akka" %% "akka-actor" % "2.5.26",
    "com.typesafe.akka" %% "akka-actor-typed" % "2.5.26",
    "com.typesafe.akka" %% "akka-slf4j" % "2.5.26",
    "com.typesafe.akka" %% "akka-stream" % "2.5.26",
    "com.typesafe.akka" %% "akka-stream-typed" % "2.5.26",
    "com.typesafe.akka" %% "akka-http-core" % "10.1.10",
    "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.26" % "test",
    "com.iheart" %% "ficus" % "1.4.7",
    "ch.qos.logback" % "logback-classic" % "1.2.3"))

lazy val assemble =
  Seq(assemblyJarName in assembly := name.value,
    target in assembly := new File("../bin/"),
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(cacheOutput = false, prependShellScript = Some(defaultShellScript :+ "")),
    test in assembly := {})

lazy val scopt = Seq(libraryDependencies += "com.github.scopt" %% "scopt" % "3.7.1")

lazy val specs2 = Seq(libraryDependencies += "org.specs2" %% "specs2-core" % "4.6.0" % "test",
  fork in Test := true)

lazy val csv = Seq(libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.3.6")

lazy val mysql =
  Seq(libraryDependencies ++= Seq("org.apache.commons" % "commons-dbcp2" % "2.7.0",
    "commons-dbutils" % "commons-dbutils" % "1.7",
    "mysql" % "mysql-connector-java" % "8.0.18"))

lazy val scalaz = Seq(libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.2.29")

lazy val common = Defaults.coreDefaultSettings ++ assemble ++
  Seq(scalaVersion := "2.13.1",
    scalariformAutoformat := true,
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
    ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(AlignArguments, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentConstructorArguments, true)
      .setPreference(SpacesWithinPatternBinders, false)
      .setPreference(SpacesAroundMultiImports, false))

lazy val pointage100km = project
  .in(file("."))
  .settings(name := "pointage100km")
  .aggregate(replicate, wipe, stats, loader)

lazy val stats = project
  .in(file("stats"))
  .settings(name := "stats", common, akka, scopt)
  .dependsOn(canape, steenwerck)

lazy val replicate = project
  .in(file("replicate"))
  .settings(name := "replicate", common, akka, scopt, specs2, csv, scalaz, Revolver.settings)
  .dependsOn(canape, steenwerck, rxtelegram, octopush)

lazy val loader = project
  .in(file("loader"))
  .settings(name := "loader", common, akka, mysql, scopt, Revolver.settings)
  .dependsOn(canape, steenwerck)

lazy val wipe = project
  .in(file("wipe"))
  .settings(name := "wipe", common, akka, scopt)
  .dependsOn(canape, steenwerck)

lazy val canape = RootProject(file("external/canape"))

lazy val octopush = RootProject(file("external/octopush-akka"))

lazy val steenwerck = project
  .in(file("libs/steenwerck"))
  .settings(name := "Steenwerck", common, akka)
  .dependsOn(canape)

lazy val rxtelegram = RootProject(file("external/rxtelegram"))
