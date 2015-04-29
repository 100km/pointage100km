package replicate.alerts

import net.rfc1149.canape.Database
import replicate.messaging.Message.Severity
import replicate.messaging.{Message, Messaging}
import replicate.utils.{Global, PeriodicTaskActor}

import scala.concurrent.Future

class BroadcastAlert(database: Database) extends PeriodicTaskActor {

  implicit val dispatcher = context.system.dispatcher

  implicit val period = Global.BroadcastAlerts.checkInterval
  override implicit val immediateStart = true

  private[this] var sentBroadcasts: Map[String, Seq[(Messaging, String)]] = Map()

  private[this] def cancelBroadcasts(ids: Seq[String]): Future[Unit] =
    Future.sequence(for (id <- ids; (messaging, identifier) <- sentBroadcasts(id))
      yield messaging.cancelMessage(identifier)).map(_ => for (id <- ids) sentBroadcasts -= id)

  private[this] def toMessage(bcast: Broadcast): Message = {
    val title = bcast.target.fold("Broadcast message")(siteId => s"Message for ${Global.infos.get.checkpoints(siteId).name}")
    Message(Message.Broadcast, Severity.Info, title = title, body = bcast.message, url = None)
  }

  private[this] def analyzeBroadcasts(broadcasts: Seq[Broadcast]): Future[Unit] = {
    val toDelete: Seq[String] = broadcasts.filter(_.deletedTS.isDefined).map(_._id).filter(sentBroadcasts.contains)
    val toSend: Seq[Broadcast] = broadcasts.filter(bcast => bcast.deletedTS.isEmpty && !sentBroadcasts.contains(bcast._id))
    cancelBroadcasts(toDelete).flatMap(_ =>
      Future.sequence(toSend.map(bcast => Alerts.deliverAlert(Alerts.officers, toMessage(bcast)).map(bcast._id -> _)))
        .map(_.foreach(sentBroadcasts += _)))
  }

  override def future = Broadcast.broadcasts(database).flatMap(analyzeBroadcasts)

}
