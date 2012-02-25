name := "canape"

organization := "net.rfc1149"

resolvers += "Typesafe repository (releases)" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq("io.netty" % "netty" % "3.3.1.Final",
                            "com.typesafe.akka" % "akka-actor" % "2.0-RC2",
			    "net.liftweb" %% "lift-json" % "2.4-RC1",
			    "org.specs2" %% "specs2" % "1.7.1" % "test")

scalacOptions ++= Seq("-unchecked", "-deprecation")
