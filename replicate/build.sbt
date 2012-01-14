name := "Replicate"

version := "0.1"

resolvers ++= Seq("RFC1149 Repository(snapshots)" at "http://maven.rfc1149.net/snapshots",
                  "Typesafe Repository (releases)" at "http://repo.typesafe.com/typesafe/releases/")

libraryDependencies ++= Seq("net.databinder" %% "dispatch-http" % "0.8.7" % "compile")
