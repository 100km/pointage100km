name := "canape"

organization := "net.rfc1149"

libraryDependencies ++= Seq("io.netty" % "netty" % "3.3.0.Final",
			    "net.liftweb" %% "lift-json" % "2.4-RC1",
			    "org.specs2" %% "specs2" % "1.7.1" % "test")

scalacOptions ++= Seq("-unchecked", "-deprecation")
