package replicate.utils

import akka.actor.{ Actor, Timers }
import akka.event.Logging
import akka.http.scaladsl.util.FastFuture
import akka.stream.ThrottleMode
import akka.stream.scaladsl.Sink
import net.rfc1149.canape._
import play.api.libs.json.JsObject
import replicate.maintenance.{ ConflictsSolver, IncompleteCheckpoints, PingService }
import replicate.utils.Global._

import scala.concurrent.Future
import scala.concurrent.duration.Deadline._
import scala.concurrent.duration._
import scala.language.postfixOps

class OnChanges(options: Options.Config, local: Database)
  extends Actor with IncompleteCheckpoints with ConflictsSolver with LoggingError with Timers {

  import OnChanges._

  val log = Logging(context.system, this)

  private[this] def incompleteCheckpoints =
    withError(
      fixIncompleteCheckpoints(local),
      "unable to get incomplete checkpoints")

  private[this] def conflictingCheckpoints =
    withError(
      fixConflictingCheckpoints(local),
      "unable to get conflicting checkpoints")

  private[this] def futures() = {
    val fc = if (options.fixConflicts) conflictingCheckpoints
    else FastFuture.successful(true)
    val fi = if (options.fixIncomplete) incompleteCheckpoints
    else FastFuture.successful(true)
    Future.sequence(List(fc, fi))
  }

  private[this] var nextRun = now

  private[this] def trigger() = {
    val f = futures()
    f.failed.foreach(log.error(_, "error when running onChanges task"))
    f.onComplete(_ ⇒ self ! 'reset)
  }

  override def preStart() =
    if (options.ping)
      PingService.launchPingService(options.siteId, local)
  local.changesSource(Map("filter" → "bib_input/no-ping"))
    .throttle(100, 1.second, 100, ThrottleMode.Shaping)
    .runWith(Sink.actorRef(self, 'ignored))
  self ! 'trigger

  override def postRestart(reason: Throwable) = {}

  override def receive() = {
    case js: JsObject ⇒
      if (!timers.isTimerActive(Timer))
        // We do not want to start the conflicts and incomplete checkpoints resolution
        // more than once every 5 seconds (rate limiting).
        timers.startSingleTimer(Timer, 'trigger, nextRun - now)
    case 'trigger ⇒
      trigger()
    case 'reset ⇒
      nextRun = now + 5.seconds
  }

}

object OnChanges {
  private case object Timer
}
