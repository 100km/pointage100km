import sbt._

object Steenwerck extends Build {

  lazy val replicate = Project("replicate", file("replicate")) dependsOn(canape)

  lazy val couchsync = Project("couchsync", file("couchsync"))

  lazy val canape = Project("canape", file("canape")) dependsOn(dispatchLiftJson)

  lazy val dispatchLiftJson = uri("git://github.com/dispatch/dispatch-lift-json#0.1.1")

}
