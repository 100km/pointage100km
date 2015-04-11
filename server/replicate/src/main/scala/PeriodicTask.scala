import akka.event.LoggingAdapter

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, FiniteDuration}

// The PeriodicTask is different from a scheduler-launched repetitive task
// as it will enforce the delay between invocations of act().

abstract class PeriodicTask(period: FiniteDuration) {

  import Global.dispatcher

  val log: LoggingAdapter

  def futures: Seq[Future[_]]

  // initialize must be called to start the periodic task
  def initialize(): Unit =
    nextIteration()

  private[this] def nextIteration(): Unit = {
    try {
      Await.ready(Future.sequence(futures), Duration.Inf)
    } catch {
      case e: Exception => log.warning("error in PeriodicTask: " + e)
    }
    Global.system.scheduler.scheduleOnce(period)(nextIteration())
  }

}
