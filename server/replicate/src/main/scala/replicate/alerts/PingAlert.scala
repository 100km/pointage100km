package replicate.alerts

import java.util.UUID

import net.rfc1149.canape.Database
import play.api.libs.json.JsObject
import replicate.messaging.Message
import replicate.messaging.Message.{Checkpoint, Severity}
import replicate.utils.Infos.CheckpointInfo
import replicate.utils.{Global, PeriodicTaskActor}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class PingAlert(database: Database, checkpointInfo: CheckpointInfo) extends PeriodicTaskActor {

  import PingAlert._

  private[this] implicit val dispatcher = context.system.dispatcher

  override val period = Global.CheckpointAlerts.checkInterval
  override val immediateStart = true

  private[this] var currentState: State = Starting

  private[this] var currentNotification: Option[UUID] = None

  private[this] def alert(severity: Severity.Severity, message: String): Unit = {
    currentNotification.foreach(Alerts.cancelAlert)
    currentNotification = Some(Alerts.sendAlert(Message(Checkpoint, severity, title = checkpointInfo.name, body = message,
      url = Some(checkpointInfo.coordinates.url))))
  }

  override def future =
    lastPing(checkpointInfo.checkpointId, database).map {
      case None =>
        if (currentState != Inactive && currentState != Starting)
          alert(Severity.Error, "Liveness data for the site has disappeared from the database")
        currentState = Inactive
      case Some(ts) =>
        val sinceLastSeen: FiniteDuration = FiniteDuration(System.currentTimeMillis() - ts, MILLISECONDS)
        val message: String = s"Site has been unresponsive for ${sinceLastSeen.toMinutes} minutes"
        val newState = timestampToState(sinceLastSeen)
        (currentState, newState) match {
          case (before, after) if before == after =>
          case (Starting, _) =>
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
        }
        currentState = newState
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

  /**
   * Return the timestamp corresponding to the last proof of live of a site.
   */
  private def lastPing(siteId: Int, database: Database)(implicit ec: ExecutionContext): Future[Option[Long]] =
    database.view[Int, JsObject]("admin", "alive",
      Seq("startkey" -> siteId.toString, "endkey" -> siteId.toString, "group" -> "true")).map { rows =>
        rows.headOption.map(row => (row._2 \ "max").as[Long])
    }

}
