package replicate.messaging.sms

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import play.api.libs.json.{JsObject, Json}
import replicate.messaging.alerts.Pushbullet
import replicate.utils.Types.PhoneNumber
import scalaz.@@

import scala.concurrent.Future
import scala.util.{Failure, Success}

object PushbulletSMS {

  def pushBulletSMS(bearerToken: String, userIden: String, deviceIden: String): Behavior[SMSMessage] = Behaviors.setup { context ⇒

    context.log.debug("PushbulletSMS service started")

    def sendSMS(recipient: String @@ PhoneNumber, message: String): Future[JsObject] =
      Pushbullet.post("/ephemerals", bearerToken, Json.obj(
        "type" → "push",
        "push" → Json.obj("type" → "messaging_extension_reply", "package_name" → "com.pushbullet.android",
          "source_user_iden" → userIden, "target_device_iden" → deviceIden, "conversation_iden" → PhoneNumber.unwrap(recipient),
          "message" → message)))

    Behaviors.receiveMessage {
      case SMSMessage(recipient, message) ⇒
        sendSMS(recipient, message).onComplete {
          case Success(_) ⇒
            context.log.info("sent message to {}: {}", recipient, message)
          case Failure(t) ⇒
            context.log.error(t, """could not send message {} to {}""", message, recipient)
        }(context.system.executionContext)
        Behaviors.same
    }

  }
}
