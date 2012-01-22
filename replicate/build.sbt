libraryDependencies ++= Seq("com.typesafe.akka" % "akka-actor" % "2.0-M2",
			    "com.typesafe.akka" % "akka-slf4j" % "2.0-M2",
                            "ch.qos.logback" % "logback-classic" % "1.0.0" % "compile")

seq(proguardSettings: _*)

minJarPath <<= mjp

proguardOptions ++= Seq(keepMain("Replicate"),
			"-keep class ch.qos.logback.** { *; }",
			"-keep class org.apache.commons.logging.** { *; }",
			"-keep public class akka.** { *; }")
