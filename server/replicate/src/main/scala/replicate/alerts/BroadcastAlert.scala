package replicate.alerts

import java.util.UUID

import akka.actor.{Actor, Props}
import net.rfc1149.canape.Database
import play.api.libs.json.{JsObject, Json, Reads}
import replicate.messaging.Message
import replicate.messaging.Message.Severity
import replicate.utils.{ChangesActor, Global}

class BroadcastAlert(database: Database) extends Actor {

  import BroadcastAlert._

  private[this] var sentBroadcasts: Map[String, UUID] = Map()

  override def preStart(): Unit = {
    context.actorOf(Props(new ChangesActor(self, database, Some("common/messages"), Map("include_docs" -> "true"))), "changes")
  }

  override val receive: Receive = {
    case json: JsObject =>
      (json \ "doc").as[Broadcast] match {
        case bcast if bcast.isDeleted =>
          sentBroadcasts.get(bcast._id) match {
            case Some(uuid) =>
              Alerts.cancelAlert(uuid)
              sentBroadcasts -= bcast._id
            case None =>
              // We did not send this broadcast, it was sent before we started
          }
        case bcast =>
          sentBroadcasts += bcast._id -> Alerts.sendAlert(bcast.toMessage)
      }
  }

}

object BroadcastAlert {

  case class Broadcast(_id: String, message: String, target: Option[Int], addedTS: Long, deletedTS: Option[Long]) {

    val isDeleted: Boolean = deletedTS.isDefined

    lazy val toMessage: Message = {
      val title = target.fold("Broadcast message")(siteId => s"Message for ${Global.infos.get.checkpoints(siteId).name}")
      Message(Message.Broadcast, Severity.Info, title = title, body = message, url = None)
    }
  }

  implicit val broadcastReads: Reads[Broadcast] = Json.reads[Broadcast]

}
