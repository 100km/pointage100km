package replicate.messaging

import akka.actor.{Actor, ActorLogging}
import play.api.libs.json.{JsValue, Json}
import replicate.utils.Global

import scala.concurrent.Future

class PushbulletSMS(bearerToken: String, userIden: String, deviceIden: String) extends Actor with ActorLogging {

  import Global.dispatcher

  private[this] def sendSMS(recipient: String, message: String): Future[JsValue] =
    Pushbullet.post("/ephemerals", bearerToken, Json.obj("type" -> "push",
      "push" -> Json.obj("type" -> "messaging_extension_reply", "package_name" -> "com.pushbullet.android",
      "source_user_iden" -> userIden, "target_device_iden" -> deviceIden, "conversation_iden" -> recipient,
      "message" -> message)))

  override val receive: Receive = {
    case (recipient: String, message: String) =>
      val result = sendSMS(recipient, message)
      result.onSuccess { case _ => log.info(s"sent message to $recipient: $message") }
      result.onFailure { case t => log.warning( s"""could not send message "$message" to $recipient: $t")""") }
  }

}

object PushbulletSMS {

  def sendSMS(recipient: String, message: String): Unit =
    Global.system.actorSelection("/user/alerts/pushbullet-sms") ! (recipient, message)

}
