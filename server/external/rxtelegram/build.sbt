import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

lazy val rxTelegram = project
  .in(file("."))
  .settings(
    name := "rxtelegram",
    organization := "net.rfc1149",
    version := "0.0.2-SNAPSHOT",
    scalaVersion := "2.12.8",
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
    resolvers ++= Seq("Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
      "Sonatype OSS Releases"  at "http://oss.sonatype.org/content/repositories/releases/",
      Resolver.jcenterRepo),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.5.21",
      "com.typesafe.akka" %% "akka-stream" % "2.5.21",
      "com.typesafe.akka" %% "akka-http-core" % "10.1.7",
      "de.heikoseeberger" %% "akka-http-play-json" % "1.22.0",
      "com.iheart" %% "ficus" % "1.4.4",
      "commons-io" % "commons-io" % "2.6",
      "org.specs2" %% "specs2-core" % "4.3.3" % "test"
    ),
    fork in Test := true,
    scalariformSettings(autoformat = true),
    ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(AlignArguments, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentConstructorArguments, true)
      .setPreference(RewriteArrowSymbols, true)
      .setPreference(SpacesWithinPatternBinders, false)
      .setPreference(SpacesAroundMultiImports, false))
