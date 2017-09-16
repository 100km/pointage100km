import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

import scalariform.formatter.preferences._

scalariformSettings(autoformat = true)

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignArguments, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentConstructorArguments, true)
  .setPreference(RewriteArrowSymbols, true)
  .setPreference(SpacesWithinPatternBinders, false)
  .setPreference(SpacesAroundMultiImports, false)

name := "octopush-akka"

organization := "net.rfc1149"

version := "0.0.2"

scalaVersion := "2.12.3"

resolvers ++= Seq("Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
                  Resolver.jcenterRepo)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.4",
  "com.typesafe.akka" %% "akka-stream" % "2.5.4",
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.4" % "test",
  "com.typesafe.akka" %% "akka-http-core" % "10.0.10",
  "com.typesafe.akka" %% "akka-http-xml" % "10.0.10",
  "com.iheart" %% "ficus" % "1.4.2",
  "org.specs2" %% "specs2-core" % "3.9.4" % "test"
)

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

fork in Test := true
