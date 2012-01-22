seq(proguardSettings: _*)

minJarPath <<= mjp

proguardOptions ++= Seq(keepMain("Replicate"),
			"-keep class ch.qos.logback.** { *; }",
			"-keep class org.apache.commons.logging.** { *; }",
			"-keep public class akka.** { *; }")
