package replicate.utils

import akka.actor.{Actor, Cancellable, Props}
import akka.event.Logging
import akka.stream.ThrottleMode
import akka.stream.scaladsl.Sink
import net.rfc1149.canape._
import play.api.libs.json.JsObject
import replicate.maintenance.{ConflictsSolver, IncompleteCheckpoints, PingService}
import replicate.utils.Global._

import scala.concurrent.Future
import scala.concurrent.duration.Deadline._
import scala.concurrent.duration._
import scala.language.postfixOps

class OnChanges(options: Options.Config, local: Database)
  extends Actor with IncompleteCheckpoints with ConflictsSolver with LoggingError {

  val log = Logging(context.system, this)

  private[this] val ping = context.actorOf(Props(new PingService(options, local)), "ping")

  private[this] def incompleteCheckpoints =
    withError(fixIncompleteCheckpoints(local),
      "unable to get incomplete checkpoints")

  private[this] def conflictingCheckpoints =
    withError(fixConflictingCheckpoints(local),
      "unable to get conflicting checkpoints")

  private[this] def futures() = {
    val fc = if (options.fixConflicts) conflictingCheckpoints
	     else Future.successful(true)
    val fi = if (options.fixIncomplete) incompleteCheckpoints
	     else Future.successful(true)
    Future.sequence(List(fc, fi))
  }

  private[this] var nextRun = now

  private[this] def trigger() = {
    val f = futures()
    f.onFailure {
      case e: Exception =>
        log.warning("error when running onChanges task: " + e)
    }
    f.onComplete(_ => self ! 'reset)
  }

  override def preStart() =
    local.changesSource(Map("filter" -> "bib_input/no-ping"))
      .throttle(100, 1.second, 100, ThrottleMode.Shaping)
      .runWith(Sink.actorRef(self, 'ignored))
    self ! 'trigger

  override def postRestart(reason: Throwable) = {}

  private[this] var timer: Option[Cancellable] = None

  override def receive() = {
    case js: JsObject =>
      if (timer.isEmpty)
        // We do not want to start the conflicts and incomplete checkpoints resolution
        // more than once every 5 seconds (rate limiting).
        timer = Some(context.system.scheduler.scheduleOnce(nextRun - now,
          self,
          'trigger))
      // For every object containing an id (XXXXX can some objects not contain an id?)
      // send it to the ping service so that it can decide whether or not a ping is needed.
      for (id <- (js \ "id").asOpt[String] if id.startsWith(s"checkpoints-${options.siteId}-"))
        ping ! js
    case 'trigger =>
      trigger()
    case 'reset =>
      timer = None
      nextRun = now + 5.seconds
  }

}

