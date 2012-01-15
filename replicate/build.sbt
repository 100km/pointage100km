name := "Replicate"

version := "0.1"

resolvers ++= Seq("Typesafe Repository (releases)" at "http://repo.typesafe.com/typesafe/releases/")

libraryDependencies ++= Seq("net.databinder" %% "dispatch-http" % "0.8.7" % "compile")
