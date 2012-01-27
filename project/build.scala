import sbt._
import Keys._
import ProguardPlugin._

object Steenwerck extends Build {

  lazy val dispatch = Seq("net.databinder" %% "dispatch-http" % "0.8.7" % "compile")

  lazy val akka =
    Seq(libraryDependencies ++= Seq("com.typesafe.akka" % "akka-actor" % "2.0-M3",
				    "com.typesafe.akka" % "akka-slf4j" % "2.0-M3",
				    "ch.qos.logback" % "logback-classic" % "1.0.0" % "compile"),
	resolvers += typesafeRepo)

  lazy val typesafeRepo = "Typesafe repository (releases)" at "http://repo.typesafe.com/typesafe/releases/"

  lazy val proguard =
    proguardSettings :+
         (proguardOptions ++= Seq("-keep class ch.qos.logback.** { *; }",
				  "-keep class org.apache.commons.logging.** { *; }",
				  "-keep public class akka.** { *; }",
				  "-keepclasseswithmembers public class * { public static void main(java.lang.String[]); }",
				  "-keep class net.rfc1149.canape.** { *; }")) :+
	 (minJarPath <<= mjp)

  lazy val scopt = Seq(libraryDependencies += "com.github.scopt" %% "scopt" % "1.1.3")

  lazy val json = Seq(libraryDependencies += "net.liftweb" %% "lift-json" % "2.4-RC1")

  lazy val jackcess =
    Seq(libraryDependencies += "com.healthmarketscience.jackcess" % "jackcess" % "1.1.18")

  lazy val root =
    Project("root", file(".")) aggregate(replicate, couchsync, wipe, canape, config)

  lazy val replicate =
    Project("replicate", file("replicate")) dependsOn(canape, config) settings(proguard: _*) settings(akka: _*) settings(scopt: _*)

  lazy val couchsync =
    Project("couchsync", file("couchsync")) dependsOn(canape) settings(proguard: _*)

  lazy val loader =
    Project("loader", file("loader")) dependsOn(canape) settings(proguard: _*) settings(jackcess: _*)

  lazy val wipe = Project("wipe", file("wipe")) dependsOn(canape, config) settings(proguard: _*)

  lazy val canape = Project("canape", file("libs/canape"))

  lazy val config = Project(id = "config", base = file("libs/config"))

  // Used by subprojects to set the proguard JAR file
  lazy val mjp = (baseDirectory, name) { (b, n) => b / ".." / "bin" / (n + ".jar") }

}
