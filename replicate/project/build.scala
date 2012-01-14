import sbt._

object Steenwerck extends Build {

  lazy val replicate = Project("replicate", file(".")) dependsOn(canape)

  lazy val canape = uri("git://github.com/samueltardieu/canape")

}
