package replicate.messaging

import akka.actor.{Actor, ActorLogging, ActorRef}
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class PushbulletSMS(bearerToken: String, userIden: String, deviceIden: String) extends Actor with ActorLogging {

  private[this] def sendSMS(recipient: String, message: String): Future[JsObject] =
    Pushbullet.post("/ephemerals", bearerToken, Json.obj("type" -> "push",
      "push" -> Json.obj("type" -> "messaging_extension_reply", "package_name" -> "com.pushbullet.android",
      "source_user_iden" -> userIden, "target_device_iden" -> deviceIden, "conversation_iden" -> recipient,
      "message" -> message)))

  override val receive: Receive = {
    case (recipient: String, message: String) =>
      sendSMS(recipient, message).onComplete {
        case Success(_) =>
          log.info(s"sent message to $recipient: $message")
        case Failure(t) =>
          log.warning( s"""could not send message "$message" to $recipient: $t")""")
      } (context.system.dispatcher)
  }

}

object PushbulletSMS {

  def sendSMS(actorRef: ActorRef, recipient: String, message: String): Unit =
    actorRef ! (recipient, message)

}
