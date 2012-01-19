import sbt._

object Steenwerck extends Build {

  lazy val rooot = Project("root", file(".")) aggregate(replicate, couchsync)

  lazy val replicate = Project("replicate", file("replicate")) dependsOn(canape, config)

  lazy val couchsync = Project("couchsync", file("couchsync")) dependsOn(canape)

  lazy val canape = Project("canape", file("libs/canape")) dependsOn(dispatchLiftJson)

  lazy val dispatchLiftJson = uri("git://github.com/dispatch/dispatch-lift-json#0.1.1")

  lazy val config = Project(id = "config", base = file("libs/config"))

}
