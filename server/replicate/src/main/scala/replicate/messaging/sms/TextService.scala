package replicate.messaging.sms

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.event.LoggingAdapter
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import replicate.alerts.Alerts
import replicate.messaging
import replicate.messaging.Message.{Severity, TextMessage}
import replicate.utils.{Global, Glyphs}

private class TextService(context: ActorContext[SMSMessage], textService: Behavior[SMSMessage], name: String) extends AbstractBehavior[SMSMessage](context) {

  val textServiceActor = context.spawn(Behaviors.supervise(textService).onFailure(SupervisorStrategy.restart), name)

  override def onMessage(msg: SMSMessage) = msg match {
    case SMSMessage(recipient, message) =>
      val alert = messaging.Message(TextMessage, Severity.Verbose, s"SMS for $recipient", message, None, Some(Glyphs.envelope))
      Alerts.sendAlert(alert)
      textServiceActor ! SMSMessage(recipient, message)
      Behaviors.same
  }

}

object TextService {

  private[this] def configuredTextService(log: LoggingAdapter): Option[(Behavior[SMSMessage], String)] = {
    val config = Global.replicateConfig.as[Config]("text-messages")
    config.as[Option[String]]("provider") match {
      case Some("pushbullet-sms") =>
        val bearerToken = config.as[String]("pushbullet-sms.bearer-token")
        val userIden = config.as[String]("pushbullet-sms.user-iden")
        val deviceIden = config.as[String]("pushbullet-sms.device-iden")
        Some((PushbulletSMS.pushBulletSMS(bearerToken, userIden, deviceIden), "pushbullet-sms"))

      case Some("nexmo") =>
        val apiKey = config.as[String]("nexmo.api-key")
        val apiSecret = config.as[String]("nexmo.api-secret")
        val senderId = config.as[String]("nexmo.sender-id")
        Some((NexmoSMS.nexmoSMS(senderId, apiKey, apiSecret), "nexmo"))

      case Some("octopush") =>
        val userLogin = config.as[String]("octopush.user-login")
        val apiKey = config.as[String]("octopush.api-key")
        val sender = config.as[Option[String]]("octopush.sender-id")
        Some((OctopushSMS.octopushSMS(userLogin, apiKey, sender), "octopush"))

      case Some("fake") =>
        Some((FakeSMS.fakeSMS, "fake-sms"))

      case Some(provider) =>
        log.error("Unknown SMS provider {} configured", provider)
        None

      case None =>
        log.info("No SMS service configured")
        None
    }
  }

  def startTextService(system: ActorSystem): Option[ActorRef[SMSMessage]] =
    configuredTextService(Global.log) map {
      case (textService, name) => system.spawn(Behaviors.setup { context => new TextService(context, textService, name) }, "text-service")
    }

}
