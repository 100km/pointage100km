import akka.event.LoggingAdapter

import scala.concurrent.duration.FiniteDuration

// The PeriodicTask is different from a scheduler-launched repetitive task
// as it will enforce the delay between invocations of act().

abstract class PeriodicTask(period: FiniteDuration) {

  import Global.dispatcher

  val log: LoggingAdapter

  def act(): Unit

  private[this] def nextIteration() {
    try act() catch { case e: Exception => log.warning("error in PeriodicActor: " + e) }
    Global.system.scheduler.scheduleOnce(period)(nextIteration())
  }

  // initialize must be called to start the periodic task
  def initialize(): Unit =
    nextIteration()

}
