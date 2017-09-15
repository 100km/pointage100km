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

name := "rxtelegram"

organization := "net.rfc1149"

version := "0.0.2-SNAPSHOT"

scalaVersion := "2.12.3"

resolvers ++= Seq("Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
                  "Sonatype OSS Releases"  at "http://oss.sonatype.org/content/repositories/releases/",
                  Resolver.jcenterRepo)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.4",
  "com.typesafe.akka" %% "akka-stream" % "2.5.4",
  "com.typesafe.akka" %% "akka-http-core" % "10.0.10",
  "de.heikoseeberger" %% "akka-http-play-json" % "1.18.0",
  "com.iheart" %% "ficus" % "1.4.2",
  "commons-io" % "commons-io" % "2.5",
  "org.specs2" %% "specs2-core" % "3.9.4" % "test"
)

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

fork in Test := true
