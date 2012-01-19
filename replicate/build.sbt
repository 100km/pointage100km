name := "Replicate"

version := "0.1"

resolvers ++= Seq("Typesafe Repository (releases)" at "http://repo.typesafe.com/typesafe/releases/")

libraryDependencies ++= Seq("net.databinder" %% "dispatch-http" % "0.8.7" % "compile",
                            "ch.qos.logback" % "logback-classic" % "1.0.0" % "compile")

seq(ProguardPlugin.proguardSettings: _*)

proguardOptions ++= Seq(keepMain("Replicate"), "-keep class ch.qos.logback.** { *; }", "-keep class org.apache.commons.logging.** { *; }")
