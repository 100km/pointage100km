package replicate.messaging.sms

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter._
import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import replicate.alerts.Alerts
import replicate.messaging
import replicate.messaging.Message.{Severity, TextMessage}
import replicate.utils.{Global, Glyphs}

class TextService extends Actor with ActorLogging {

  private[this] var textService: ActorRef[SMSMessage] = _

  private[this] def startTextService(): Option[ActorRef[SMSMessage]] = {
    val config = Global.replicateConfig.as[Config]("text-messages")
    config.as[Option[String]]("provider") match {
      case Some("pushbullet-sms") ⇒
        val bearerToken = config.as[String]("pushbullet-sms.bearer-token")
        val userIden = config.as[String]("pushbullet-sms.user-iden")
        val deviceIden = config.as[String]("pushbullet-sms.device-iden")
        Some(context.spawn(PushbulletSMS.pushBulletSMS(bearerToken, userIden, deviceIden), "pushbullet-sms"))

      case Some("nexmo") ⇒
        val apiKey = config.as[String]("nexmo.api-key")
        val apiSecret = config.as[String]("nexmo.api-secret")
        val senderId = config.as[String]("nexmo.sender-id")
        Some(context.spawn(NexmoSMS.nexmoSMS(senderId, apiKey, apiSecret), "nexmo"))

      case Some("octopush") ⇒
        val userLogin = config.as[String]("octopush.user-login")
        val apiKey = config.as[String]("octopush.api-key")
        val sender = config.as[Option[String]]("octopush.sender-id")
        Some(context.spawn(OctopushSMS.octopushSMS(userLogin, apiKey, sender), "octopush"))

      case Some("fake") ⇒
        Some(context.spawn(FakeSMS.fakeSMS, "fake-sms"))

      case Some(provider) ⇒
        log.error("Unknown SMS provider {} configured", provider)
        None

      case None ⇒
        log.info("No SMS service configured")
        None
    }
  }

  override def preStart() = {
    startTextService() match {
      case Some(service) ⇒ textService = service
      case None          ⇒ context.stop(self)
    }
  }

  def receive = {
    case SMSMessage(recipient, message) ⇒
      val alert = messaging.Message(TextMessage, Severity.Verbose, s"SMS for $recipient", message, None, Some(Glyphs.envelope))
      Alerts.sendAlert(alert)
      textService ! SMSMessage(recipient, message)
  }

}
