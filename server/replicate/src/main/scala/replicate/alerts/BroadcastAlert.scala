package replicate.alerts

import java.util.UUID

import akka.actor.typed.scaladsl.ActorContext
import akka.stream.Materializer
import akka.stream.typed.scaladsl._
import net.rfc1149.canape.Database
import play.api.libs.json._
import play.api.libs.functional.syntax._
import replicate.messaging
import replicate.messaging.Message
import replicate.messaging.Message.Severity
import replicate.utils.Types._
import replicate.utils.{Global, Glyphs}
import scalaz.@@

private class BroadcastAlert {

  import BroadcastAlert._

  private[this] var sentBroadcasts: Map[String, UUID] = Map()

  def sendOrCancelBroadcast(json: JsObject): Unit =
    try {
      (json \ "doc").validate[Broadcast] match {
        case JsSuccess(bcast, _) =>
          if (bcast.isDeleted) {
            sentBroadcasts.get(bcast._id) match {
              case Some(uuid) =>
                Alerts.cancelAlert(uuid)
                sentBroadcasts -= bcast._id
              case None =>
              // We did not send this broadcast, it was sent before we started
            }
          } else
            sentBroadcasts += bcast._id -> Alerts.sendAlert(bcast.toMessage)
        case error: JsError =>
          Global.system.log.error("BroadcastAlert: unable to analyze alert {}: {}", (json \ "id").as[String], error);
      }
    } catch {
      case throwable: Throwable =>
        Global.system.log.error(throwable, "BroadcastAlert: crash when analyzing {}", (json \ "id").as[String]);
    }
}

object BroadcastAlert {

  case class Broadcast(_id: String, message: String, target: Option[Int @@ SiteId], addedTS: Long, deletedTS: Option[Long]) {

    val isDeleted: Boolean = deletedTS.isDefined

    lazy val toMessage: Message = {
      val title = target.fold("Broadcast message")(siteId => s"Message for ${Global.infos.get.checkpoints(siteId).name}")
      val icon = if (target.isDefined) Glyphs.telephoneReceiver else Glyphs.publicAddressLoudspeaker
      messaging.Message(Message.Broadcast, Severity.Info, title = title, body = message, url = None, icon = Some(icon))
    }
  }

  // XXXXX Why doesn't this work? It used to before Scala 2.13
  // implicit val broadcastReads: Reads[Broadcast] = Json.reads[Broadcast]

  implicit val broadcastReads: Reads[Broadcast] = Reads { js =>
    try {
      val id = (js \ "_id").as[String]
      val message = (js \ "message").as[String]
      val target = (js \ "target").asOpt[Int].map(SiteId.apply)
      val addedTS = (js \ "addedTS").as[Long]
      val deletedTS = (js \ "deletedTS").asOpt[Long]
      JsSuccess(Broadcast(id, message, target, addedTS, deletedTS))
    } catch {
      case t: Throwable => JsError(t.getMessage)
    }
  }

  def runBroadcastAlerts(database: Database)(context: ActorContext[_]) = {
    val broadcaster = new BroadcastAlert
    database.changesSource(Map("filter" -> "replicate/messages", "include_docs" -> "true"))
      .runForeach(broadcaster.sendOrCancelBroadcast)(Materializer(context.system))
  }

}
