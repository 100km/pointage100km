name := "Replicate"

version := "0.1"

resolvers ++= Seq("Typesafe Repository (releases)" at "http://repo.typesafe.com/typesafe/releases/")

libraryDependencies ++= Seq("se.scalablesolutions.akka" % "akka-actor" % "1.2",
                            "net.databinder" %% "dispatch-http" % "0.8.6",
			    "net.databinder" %% "dispatch-json" % "0.8.6")
