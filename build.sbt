name := "pointage100km"

version := "1.0"

scalaVersion := "2.9.1"

// Add akka repository so that akka-actor can be found
// TODO check when version 2.0 come to Maven repository
resolvers += "Akka Maven2 Repository" at "http://akka.io/repository/"


libraryDependencies += "se.scalablesolutions.akka" % "akka-sbt-plugin" % "1.2"

libraryDependencies += "se.scalablesolutions.akka" % "akka-actor" % "1.2"

libraryDependencies += "se.scalablesolutions.akka" % "akka-amqp" % "1.2"
