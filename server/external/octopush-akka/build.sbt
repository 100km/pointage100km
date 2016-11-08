import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

SbtScalariform.scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignArguments, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(RewriteArrowSymbols, true)
  .setPreference(SpacesWithinPatternBinders, false)
  .setPreference(SpacesAroundMultiImports, false)

name := "octopush-akka"

organization := "net.rfc1149"

version := "0.0.1"

scalaVersion := "2.11.8"

resolvers ++= Seq("Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
                  Resolver.jcenterRepo)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.12",
  "com.typesafe.akka" %% "akka-stream" % "2.4.12",
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.4.12" % "test",
  "com.typesafe.akka" %% "akka-http-core" % "2.4.11",
  "com.typesafe.akka" %% "akka-http-experimental" % "2.4.11",
  "com.typesafe.akka" %% "akka-http-xml-experimental" % "2.4.11",
  "com.iheart" %% "ficus" % "1.2.6",
  "org.specs2" %% "specs2-core" % "3.8.5.1" % "test"
)

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

fork in Test := true

publishTo := {
  val path = "/home/sam/rfc1149.net/data/ivy2/" + (if (version.value.trim.endsWith("SNAPSHOT")) "snapshots/" else "releases")
  Some(Resolver.ssh("rfc1149 ivy releases", "rfc1149.net", path) as "sam" withPermissions("0644"))
}
