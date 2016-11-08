import sbt._
import Keys._
import sbtassembly.AssemblyPlugin.autoImport._
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

SbtScalariform.scalariformSettings

lazy val akka =
  Seq(libraryDependencies ++= Seq("com.typesafe.akka" %% "akka-actor" % "2.4.12",
                                  "com.typesafe.akka" %% "akka-slf4j" % "2.4.12",
                                  "com.typesafe.akka" %% "akka-agent" % "2.4.12",
                                  "com.typesafe.akka" %% "akka-stream" % "2.4.12",
                                  "com.typesafe.akka" %% "akka-http-core" % "2.4.11",
                                  "com.typesafe.akka" %% "akka-stream-testkit" % "2.4.12" % "test",
                                  "com.iheart" %% "ficus" % "1.2.6",
                                  "ch.qos.logback" % "logback-classic" % "1.1.7"))

lazy val defaultShellScript = Seq("#! /bin/sh", """exec java -jar "$0" "$@"""")

lazy val assemble =
  Seq(assemblyJarName in assembly := name.value,
      target in assembly := new File("../bin/"),
      assemblyOption in assembly := (assemblyOption in assembly).value.copy(prependShellScript = Some(defaultShellScript)),
      test in assembly := {})

lazy val scopt = Seq(libraryDependencies += "com.github.scopt" %% "scopt" % "3.5.0")

lazy val specs2 = Seq(libraryDependencies += "org.specs2" %% "specs2-core" % "3.8.5.1" % "test",
                      fork in Test := true)

lazy val csv = Seq(libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.3.3")

lazy val mysql =
  Seq(libraryDependencies ++= Seq("org.apache.commons" % "commons-dbcp2" % "2.1.1",
                                  "commons-dbutils" % "commons-dbutils" % "1.6",
                                  "mysql" % "mysql-connector-java" % "6.0.5"))

lazy val scalaz = Seq(libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.2.4")

lazy val common = Defaults.coreDefaultSettings ++ assemble ++
  Seq(scalaVersion := "2.11.8",
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      resolvers ++= Seq("Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/", Resolver.jcenterRepo),
      ScalariformKeys.preferences := ScalariformKeys.preferences.value
        .setPreference(AlignArguments, true)
        .setPreference(AlignSingleLineCaseStatements, true)
        .setPreference(DoubleIndentClassDeclaration, true)
        .setPreference(RewriteArrowSymbols, true)
        .setPreference(SpacesWithinPatternBinders, false)
        .setPreference(SpacesAroundMultiImports, false))

lazy val pointage100km =
  Project("pointage100km", file(".")) aggregate(replicate, wipe, stats, loader)

lazy val stats =
  Project("stats", file("stats"), settings = common ++ akka ++ scopt) dependsOn(canape, steenwerck)

lazy val replicate =
  Project("replicate", file("replicate"), settings = common ++ akka ++ scopt ++ specs2 ++
    csv ++ scalaz ++ Revolver.settings) dependsOn(canape, steenwerck, rxtelegram, octopush)

lazy val loader =
  Project("loader", file("loader"), settings = common ++ akka ++ mysql ++ scopt ++ Revolver.settings) dependsOn(canape, steenwerck)

lazy val wipe = Project("wipe", file("wipe"), settings = common ++ akka ++ scopt) dependsOn(canape, steenwerck)

lazy val canape = RootProject(file("external/canape"))

lazy val octopush = RootProject(file("external/octopush-akka"))

lazy val steenwerck = Project("steenwerck", file("libs/steenwerck"), settings = common ++ akka) dependsOn(canape)

lazy val rxtelegram = RootProject(file("external/rxtelegram"))
