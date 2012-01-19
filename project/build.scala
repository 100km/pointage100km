import sbt._

object Steenwerck extends Build {

  lazy val rooot = Project("root", file(".")) aggregate(replicate, couchsync)

  lazy val replicate = Project("replicate", file("replicate")) dependsOn(canape)

  lazy val couchsync = Project("couchsync", file("couchsync")) dependsOn(canape)

  lazy val canape = Project("canape", file("canape")) dependsOn(dispatchLiftJson)

  lazy val dispatchLiftJson = uri("git://github.com/dispatch/dispatch-lift-json#0.1.1")

}
