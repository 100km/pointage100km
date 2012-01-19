resolvers ++= Seq("Typesafe Repository (releases)" at "http://repo.typesafe.com/typesafe/releases/")

libraryDependencies ++= Seq("net.databinder" %% "dispatch-http" % "0.8.7" % "compile")

seq(ProguardPlugin.proguardSettings: _*)

proguardOptions += keepMain("Wipe")
