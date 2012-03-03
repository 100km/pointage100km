import akka.actor.{Actor, Cancellable, Props}
import akka.dispatch.{Await, Future}
import akka.event.Logging
import akka.util.Deadline._
import akka.util.Duration
import akka.util.duration._
import net.liftweb.json._
import net.rfc1149.canape._

import Global._

class OnChanges(local: Database)
  extends Actor with IncompleteCheckpoints with ConflictsSolver {

  val log = Logging(context.system, this)

  private[this] val watchdog = context.actorOf(Props(new Watchdog(local)), "watchdog")

  private[this] def withError[T](future: Future[T], message: String): Future[Any] =
    future onFailure {
      case e: Exception => log.warning(message + ": " + e)
    }

  private[this] def incompleteCheckpoints =
    withError(fixIncompleteCheckpoints(local),
      "unable to get incomplete checkpoints")

  private[this] def conflictingCheckpoints =
    withError(fixConflictingCheckpoints(local),
      "unable to get conflicting checkpoints")

  private[this] def futures =
    Future.sequence(List(incompleteCheckpoints, conflictingCheckpoints))

  private[this] var changesOccurred = false

  private[this] var nextRun = now

  private[this] def trigger() {
    try {
      Await.ready(futures, Duration.Inf)
    } catch {
      case e: Exception =>
	log.warning("error when running onChanges task: " + e)
    }
    nextRun = now + (5 seconds)
  }

  context.actorOf(Props(new ChangesActor(self, local, Some("bib_input/race-related"))),
    "changes")

  override def preStart() =
    self ! 'trigger

  override def postRestart(reason: Throwable) = {}

  private[this] var timer: Option[Cancellable] = None

  override def receive() = {
    case js: JObject =>
      if (!timer.isDefined)
	timer = Some(context.system.scheduler.scheduleOnce(nextRun - now,
							   self,
							   'trigger))
      js \ "id" match {
	  case JString(s) if s.startsWith("checkpoints-" + Replicate.siteId + "-") =>
	    watchdog ! js
	  case _ =>
      }
    case 'trigger =>
      trigger()
      timer = None
  }

}
