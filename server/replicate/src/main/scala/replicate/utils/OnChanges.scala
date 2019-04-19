package replicate.utils

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.util.FastFuture
import akka.stream.ThrottleMode
import akka.stream.typed.scaladsl.{ActorMaterializer, ActorSink}
import net.rfc1149.canape.Database
import play.api.libs.json.JsObject
import replicate.maintenance.{ConflictsSolver, IncompleteCheckpoints, PingService}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object OnChanges {
  private case object Timer

  def onChanges(options: Options.Config, local: Database): Behavior[OnChangesProtocol] = Behaviors.setup[OnChangesProtocol] { context ⇒
    Behaviors.withTimers { timers ⇒

      import context.executionContext

      val utils = new IncompleteCheckpoints with ConflictsSolver {
        override val log = context.log

        def withError[T](future: Future[T], message: String): Future[Any] = {
          future.failed.foreach(log.error(_, message))
          future
        }
      }

      import utils._

      def incompleteCheckpoints =
        withError(
          fixIncompleteCheckpoints(local),
          "unable to get incomplete checkpoints")

      def conflictingCheckpoints =
        withError(
          fixConflictingCheckpoints(local),
          "unable to get conflicting checkpoints")

      def futures() = {
        val fc = if (options.fixConflicts) conflictingCheckpoints
        else FastFuture.successful(true)
        val fi = if (options.fixIncomplete) incompleteCheckpoints
        else FastFuture.successful(true)
        Future.sequence(List(fc, fi))
      }

      var nextRun = Deadline.now

      def trigger() = {
        val f = futures()
        f.failed.foreach(log.error(_, "error when running onChanges task"))
        f.onComplete(_ ⇒ context.self ! Reset)
      }

      val materializer = ActorMaterializer.boundToActor(context)

      if (options.ping)
        PingService.launchPingService(options.siteId, local)(materializer)

      local.changesSource(Map("filter" → "bib_input/no-ping"))
        .throttle(100, 1.second, 100, ThrottleMode.Shaping)
        .map(Data)
        .runWith(ActorSink.actorRef(context.self, StreamCompleted, StreamFailed))(materializer)

      context.self ! Trigger

      Behaviors.receiveMessage {
        case Data(js) ⇒
          if (!timers.isTimerActive(Timer))
            // We do not want to start the conflicts and incomplete checkpoints resolution
            // more than once every 5 seconds (rate limiting).
            timers.startSingleTimer(Timer, Trigger, nextRun - Deadline.now)
          Behaviors.same
        case Trigger ⇒
          trigger()
          Behaviors.same
        case Reset ⇒
          nextRun = Deadline.now + 5.seconds
          Behaviors.same
        case StreamCompleted ⇒
          log.error("changes source completed, this is not supposed to happen, aborting")
          Behaviors.stopped
        case StreamFailed(e) ⇒
          log.error(e, "changes source terminated on error, aborting")
          Behaviors.stopped
      }
    }

  }

  sealed trait OnChangesProtocol
  case class Data(js: JsObject) extends OnChangesProtocol
  private case object Trigger extends OnChangesProtocol
  private case object Reset extends OnChangesProtocol
  private case object StreamCompleted extends OnChangesProtocol
  private case class StreamFailed(e: Throwable) extends OnChangesProtocol

}
