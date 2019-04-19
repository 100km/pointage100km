import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._

lazy val octopushAkka = project
    .in(file("."))
    .settings(
      name := "octopush-akka",
      organization := "net.rfc1149",
      version := "0.0.2",
      scalaVersion := "2.12.8",
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      resolvers ++= Seq("Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
        Resolver.jcenterRepo),
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % "2.5.22",
        "com.typesafe.akka" %% "akka-stream" % "2.5.22",
        "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.22" % "test",
        "com.typesafe.akka" %% "akka-http-core" % "10.1.8",
        "com.typesafe.akka" %% "akka-http-xml" % "10.1.8",
        "com.iheart" %% "ficus" % "1.4.4",
        "org.specs2" %% "specs2-core" % "4.3.4" % "test"
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
