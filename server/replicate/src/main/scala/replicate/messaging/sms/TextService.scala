package replicate.messaging.sms

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import replicate.utils.Global

class TextService extends Actor with ActorLogging {

  private[this] var textService: ActorRef = _

  private[this] def startTextService(): Option[ActorRef] = {
    val config = Global.replicateConfig.as[Config]("text-messages")
    config.as[Option[String]]("provider") match {
      case Some("pushbullet-sms") ⇒
        val bearerToken = config.as[String]("pushbullet-sms.bearer-token")
        val userIden = config.as[String]("pushbullet-sms.user-iden")
        val deviceIden = config.as[String]("pushbullet-sms.device-iden")
        Some(context.actorOf(Props(new PushbulletSMS(bearerToken, userIden, deviceIden)), "pushbullet-sms"))

      case Some("nexmo") ⇒
        val apiKey = config.as[String]("nexmo.api-key")
        val apiSecret = config.as[String]("nexmo.api-secret")
        val senderId = config.as[String]("nexmo.sender-id")
        Some(context.actorOf(Props(new NexmoSMS(senderId, apiKey, apiSecret)), "nexmo"))

      case Some("octopush") ⇒
        val userLogin = config.as[String]("octopush.user-login")
        val apiKey = config.as[String]("octopush.api-key")
        val sender = config.as[Option[String]]("octopush.sender-id")
        Some(context.actorOf(Props(new OctopushSMS(userLogin, apiKey, sender)), "octopush"))

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
    case m@(recipient: String, message: String) ⇒ textService.forward(m)
  }

}
