package replicate.alerts

import java.util.UUID

import akka.stream.{Materializer, ThrottleMode}
import net.rfc1149.canape.Database
import play.api.libs.json.{JsObject, Json, Reads}
import replicate.messaging.Message
import replicate.messaging.Message.Severity
import replicate.utils.{Global, Glyphs}
import scala.concurrent.duration._

class Broadcaster {

  import BroadcastAlert._

  private[this] var sentBroadcasts: Map[String, UUID] = Map()

  def sendOrCancelBroadcast(doc: JsObject): Unit =
    doc match {
      case json: JsObject ⇒
        (json \ "doc").as[Broadcast] match {
          case bcast if bcast.isDeleted ⇒
            sentBroadcasts.get(bcast._id) match {
              case Some(uuid) ⇒
                Alerts.cancelAlert(uuid)
                sentBroadcasts -= bcast._id
              case None ⇒
              // We did not send this broadcast, it was sent before we started
            }
          case bcast ⇒
            sentBroadcasts += bcast._id → Alerts.sendAlert(bcast.toMessage)
        }
    }

}

object BroadcastAlert {

  case class Broadcast(_id: String, message: String, target: Option[Int], addedTS: Long, deletedTS: Option[Long]) {

    val isDeleted: Boolean = deletedTS.isDefined

    lazy val toMessage: Message = {
      val title = target.fold("Broadcast message")(siteId ⇒ s"Message for ${Global.infos.get.checkpoints(siteId).name}")
      val icon = if (target.isDefined) Glyphs.telephoneReceiver else Glyphs.publicAddressLoudspeaker
      Message(Message.Broadcast, Severity.Info, title = title, body = message, url = None, icon = Some(icon))
    }
  }

  implicit val broadcastReads: Reads[Broadcast] = Json.reads[Broadcast]

  def runBroadcastAlerts(database: Database)(implicit materializer: Materializer) = {
    val broadcaster = new Broadcaster
    database.changesSource(Map("filter" → "common/messages", "include_docs" → "true"))
      .throttle(1, 1.minute, 5, ThrottleMode.Shaping) // Do not make phones unusable by more than one alert every minute
      .runForeach(broadcaster.sendOrCancelBroadcast)
  }

}
