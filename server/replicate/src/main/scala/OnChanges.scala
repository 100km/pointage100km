import Global._
import akka.actor.{Actor, Cancellable, Props}
import akka.event.Logging
import net.rfc1149.canape._
import play.api.libs.json.{JsString, JsObject}

import scala.concurrent.duration.Deadline._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class OnChanges(options: Options.Config, local: Database)
  extends Actor with IncompleteCheckpoints with ConflictsSolver {

  val log = Logging(context.system, this)

  private[this] val watchdog = context.actorOf(Props(new Watchdog(options, local)), "watchdog")

  private[this] def withError[T](future: Future[T], message: String): Future[Any] = {
    future onFailure {
      case e: Exception => log.warning(message + ": " + e)
    }
    future
  }

  private[this] def incompleteCheckpoints =
    withError(fixIncompleteCheckpoints(local),
      "unable to get incomplete checkpoints")

  private[this] def conflictingCheckpoints =
    withError(fixConflictingCheckpoints(local),
      "unable to get conflicting checkpoints")

  private[this] def futures = {
    val fc = if (options.fixConflicts) conflictingCheckpoints
	     else Future.successful(true)
    val fi = if (options.fixIncomplete) incompleteCheckpoints
	     else Future.successful(true)
    Future.sequence(List(fc, fi))
  }

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
    case js: JsObject =>
      if (!timer.isDefined)
        timer = Some(context.system.scheduler.scheduleOnce(nextRun - now,
          self,
          'trigger))
      js \ "id" match {
        case JsString(s) if s.startsWith("checkpoints-" + options.siteId + "-") =>
          watchdog ! js
        case _ =>
      }
    case 'trigger =>
      trigger()
      timer = None
  }

}
