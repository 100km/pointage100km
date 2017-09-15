package replicate.utils

import akka.actor.Status.Failure
import akka.actor.{ Actor, ActorLogging, Timers }
import akka.pattern.pipe

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

// The PeriodicTask is different from a scheduler-launched repetitive task
// as it will enforce the delay between invocations of act().

trait PeriodicTaskActor extends Actor with ActorLogging with Timers {

  import Global.dispatcher
  import PeriodicTaskActor._

  /**
   * Time to wait between invocations.
   */
  def period: FiniteDuration

  /**
   * The future to invoke if only one is needed.
   */
  def future: Future[Any] = Future.failed(new IllegalStateException("future/futures has not been defined"))

  /**
   * The futures to invoke if several ones are needed.
   */
  def futures: Seq[Future[Any]] = Seq(future)

  /**
   * Should the invocation be immediate when the actor is started?
   */
  def immediateStart: Boolean = false

  override def preStart() = {
    log.debug("starting")
    self ! (if (immediateStart) 'act else 'wait)
  }

  final def receive = {
    case 'act ⇒
      log.debug("starting periodic work")
      Future.sequence(futures).pipeTo(self)
    case 'wait ⇒
      log.debug("waiting for {}", period)
      timers.startSingleTimer(Timer, 'act, period)
    case Failure(e) ⇒
      log.error(e, "error in execution")
      self ! 'wait
    case _ ⇒
      log.debug("execution done")
      self ! 'wait
  }

}

object PeriodicTaskActor {
  private case object Timer
}
