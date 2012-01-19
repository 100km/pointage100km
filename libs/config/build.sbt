scalacOptions ++= Seq("-unchecked", "-deprecation")

resolvers += "Typesafe Repository (releases)" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq("org.ini4j" % "ini4j" % "0.5.2")
