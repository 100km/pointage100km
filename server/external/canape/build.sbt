import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._

lazy val canape = project
    .in(file("."))
    .settings(
      name := "canape",
      organization := "net.rfc1149",
      version := "0.0.9-SNAPSHOT",
      scalaVersion := "2.12.8",
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      resolvers ++= Seq("Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
            Resolver.jcenterRepo),
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % "2.5.19",
        "com.typesafe.akka" %% "akka-stream" % "2.5.19",
        "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.19" % "test",
        "com.typesafe.akka" %% "akka-http" % "10.1.5",
        "de.heikoseeberger" %% "akka-http-play-json" % "1.22.0",
        "com.iheart" %% "ficus" % "1.4.2",
        "org.specs2" %% "specs2-core" % "4.3.3" % "test",
        "org.specs2" %% "specs2-mock" % "4.3.3" % "test"
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

