package replicate.messaging

import akka.actor.{Actor, ActorLogging}
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class PushbulletSMS(bearerToken: String, userIden: String, deviceIden: String) extends Actor with ActorLogging {

  override def preStart =
    log.debug("PushbulletSMS service started")

  private[this] def sendSMS(recipient: String, message: String): Future[JsObject] =
    Pushbullet.post("/ephemerals", bearerToken, Json.obj("type" -> "push",
      "push" -> Json.obj("type" -> "messaging_extension_reply", "package_name" -> "com.pushbullet.android",
      "source_user_iden" -> userIden, "target_device_iden" -> deviceIden, "conversation_iden" -> recipient,
      "message" -> message)))

  override val receive: Receive = {
    case (recipient: String, message: String) =>
      sendSMS(recipient, message).onComplete {
        case Success(_) =>
          log.info("sent message to {}: {}", recipient, message)
        case Failure(t) =>
          log.error(t, """could not send message {} to {}""", message, recipient)
      } (context.system.dispatcher)
  }

}
