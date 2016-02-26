package replicate.alerts

import java.util.UUID

import akka.actor.Actor
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, ThrottleMode}
import net.rfc1149.canape.Database
import play.api.libs.json.{JsObject, Json, Reads}
import replicate.messaging.Message
import replicate.messaging.Message.Severity
import replicate.utils.{Global, Glyphs}

import scala.concurrent.duration._

class BroadcastAlert(database: Database) extends Actor {

  import BroadcastAlert._

  implicit val materializer = ActorMaterializer.create(context)

  private[this] var sentBroadcasts: Map[String, UUID] = Map()

  override def preStart(): Unit = {
    database.changesSource(Map("filter" -> "common/messages", "include_docs" -> "true"))
      .throttle(10, 1.second, 10, ThrottleMode.Shaping)
      .runWith(Sink.actorRef(self, 'ignored))
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
      val icon = if (target.isDefined) Glyphs.telephoneReceiver else Glyphs.publicAddressLoudspeaker
      Message(Message.Broadcast, Severity.Info, title = title, body = message, url = None, icon = Some(icon))
    }
  }

  implicit val broadcastReads: Reads[Broadcast] = Json.reads[Broadcast]

}
