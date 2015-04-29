package replicate.alerts

import net.rfc1149.canape.Database
import replicate.messaging.Message.{Checkpoint, Severity}
import replicate.messaging.{Message, Messaging}
import replicate.utils.Infos.CheckpointInfo
import replicate.utils.{Global, PeriodicTaskActor}

import scala.concurrent.Future
import scala.concurrent.duration._

class PingAlert(database: Database, checkpointInfo: CheckpointInfo) extends PeriodicTaskActor {

  import PingAlert._

  private[this] implicit val dispatcher = context.system.dispatcher

  override val period = Global.CheckpointAlerts.checkInterval
  override val immediateStart = true

  private[this] var currentState: State = Starting

  private[this] var currentNotifications: Seq[(Messaging, String)] = Seq()

  private[this] def cancelPreviousNotifications(notifications: Seq[(Messaging, String)]): Future[Unit] =
    Future.sequence(currentNotifications.map {
      case (messaging, identifier) => messaging.cancelMessage(identifier)
    }).map(_ => currentNotifications = Seq())

  private[this] def alert(severity: Severity.Severity, message: String): Future[Unit] = {
    cancelPreviousNotifications(currentNotifications)
    Alerts.deliverAlert(Alerts.officers, Message(Checkpoint, severity, title = checkpointInfo.name, body = message, url = None))
      .map(currentNotifications = _)
  }

  override def future =
    Ping.lastPing(checkpointInfo.checkpointId, database).flatMap {
      case None =>
        Future.successful(currentState = Inactive)
      case Some(ts) =>
        val sinceLastSeen: FiniteDuration = FiniteDuration(System.currentTimeMillis() - ts, MILLISECONDS)
        val message: String = s"Site has been unresponsive for ${sinceLastSeen.toMinutes} minutes"
        val newState = timestampToState(sinceLastSeen)
        ((currentState, newState) match {
          case (before, after) if before == after =>
            Future.successful(())
          case (Starting, _) =>
            Future.successful(())
          case (Inactive, Up) =>
            alert(Severity.Verbose, "Site went up for the first time")
          case (_, Notice) =>
            alert(Severity.Info, message)
          case (_, Warning) =>
            alert(Severity.Warning, message)
          case (_, Critical) =>
            alert(Severity.Critical, message)
          case (_, Up) =>
            alert(Severity.Info, "Site is back up")
          case (_, _) =>
            log.error(s"Impossible checkpoint state transition from $currentState to $newState")
            Future.successful(())
        }).map(_ => currentState = newState)
    }

}

object PingAlert {

  private sealed trait State
  private case object Starting extends State
  private case object Inactive extends State
  private case object Up extends State
  private case object Notice extends State
  private case object Warning extends State
  private case object Critical extends State

  private def timestampToState(sinceLastSeen: FiniteDuration): State = {
    if (sinceLastSeen >= Global.CheckpointAlerts.criticalDelay)
      Critical
    else if (sinceLastSeen >= Global.CheckpointAlerts.warningDelay)
      Warning
    else if (sinceLastSeen >= Global.CheckpointAlerts.noticeDelay)
      Notice
    else
      Up
  }

}
