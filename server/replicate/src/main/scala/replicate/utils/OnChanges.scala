package replicate.utils

import akka.actor.{Actor, Cancellable, Props}
import akka.event.Logging
import net.rfc1149.canape._
import play.api.libs.json.JsObject
import replicate.maintenance.{ConflictsSolver, IncompleteCheckpoints, PingService}
import replicate.utils.Global._

import scala.concurrent.duration.Deadline._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class OnChanges(options: Options.Config, local: Database)
  extends Actor with IncompleteCheckpoints with ConflictsSolver {

  val log = Logging(context.system, this)

  private[this] val ping = context.actorOf(Props(new PingService(options, local)), "ping")

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

  context.actorOf(Props(new ChangesActor(self, local, Some("bib_input/no-ping"))),
    "changes")

  override def preStart() =
    self ! 'trigger

  override def postRestart(reason: Throwable) = {}

  private[this] var timer: Option[Cancellable] = None

  override def receive() = {
    case js: JsObject =>
      if (timer.isEmpty)
        timer = Some(context.system.scheduler.scheduleOnce(nextRun - now,
          self,
          'trigger))
      (js \ "id").asOpt[String] match {
        case Some(s) if s.startsWith(s"checkpoints-${options.siteId}-") => ping ! js
        case _ =>
      }
    case 'trigger =>
      trigger()
      timer = None
  }

}
