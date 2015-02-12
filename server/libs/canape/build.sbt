name := "canape"

organization := "net.rfc1149"

version := "0.0.6-SNAPSHOT"

scalaVersion := "2.11.5"

resolvers += "Typesafe repository (releases)" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq("io.netty" % "netty" % "3.3.1.Final",
                            "com.typesafe.akka" %% "akka-actor" % "2.3.9",
			    "net.liftweb" %% "lift-json" % "2.6",
			    "org.specs2" %% "specs2-core" % "2.4.15" % "test")

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

publishMavenStyle := true

publishTo <<= (version) { version: String =>
  val path = "/home/sam/rfc1149.net/data/maven2/" +
      (if (version.trim.endsWith("SNAPSHOT")) "snapshots/" else "releases")
  Some(Resolver.sftp("Maven Releases", "rfc1149.net", path) as "sam")
}
