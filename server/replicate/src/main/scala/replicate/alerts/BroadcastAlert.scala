package replicate.alerts

import java.util.UUID

import net.rfc1149.canape.Database
import replicate.messaging.Message
import replicate.messaging.Message.Severity
import replicate.utils.{Global, PeriodicTaskActor}

class BroadcastAlert(database: Database) extends PeriodicTaskActor {

  implicit val dispatcher = context.system.dispatcher

  implicit val period = Global.BroadcastAlerts.checkInterval
  override implicit val immediateStart = true

  private[this] var sentBroadcasts: Map[String, UUID] = Map()

  private[this] var nextTimestamp: Option[Long] = None

  private[this] def cancelBroadcasts(ids: Seq[String]): Unit = {
    ids.foreach(id => Alerts.cancelAlert(sentBroadcasts(id)))
    sentBroadcasts --= ids
  }

  private[this] def toMessage(bcast: Broadcast): Message = {
    val title = bcast.target.fold("Broadcast message")(siteId => s"Message for ${Global.infos.get.checkpoints(siteId).name}")
    Message(Message.Broadcast, Severity.Info, title = title, body = bcast.message, url = None)
  }

  private[this] def analyzeBroadcasts(timestampedBroadcasts: Seq[(Long, Broadcast)]): Unit = {
    timestampedBroadcasts.lastOption.foreach(tsbcast => nextTimestamp = Some(tsbcast._1 + 1))
    val broadcasts = timestampedBroadcasts.map(_._2)
    val toDelete: Seq[String] = broadcasts.filter(_.deletedTS.isDefined).map(_._id).filter(sentBroadcasts.contains)
    val toSend: Seq[Broadcast] = broadcasts.filter(bcast => bcast.deletedTS.isEmpty && !sentBroadcasts.contains(bcast._id))
    cancelBroadcasts(toDelete)
    sentBroadcasts ++= toSend.map(bcast => bcast._id -> Alerts.sendAlert(toMessage(bcast))).toMap
  }

  override def future = Broadcast.broadcasts(database, nextTimestamp).map(analyzeBroadcasts)

}
