package replicate.messaging.sms

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import net.rfc1149.octopush.Octopush
import net.rfc1149.octopush.Octopush.{PremiumFrance, SMS, SMSResult, WWW}
import replicate.alerts.Alerts
import replicate.messaging
import replicate.messaging.Message.{Severity, TextMessage}
import replicate.utils.Types.PhoneNumber
import replicate.utils.{FormatUtils, Glyphs}

import scala.util.{Failure, Success}

class OctopushSMS(context: ActorContext[SMSProtocol], userLogin: String, apiKey: String, sender: Option[String]) extends AbstractBehavior[SMSProtocol](context) with BalanceTracker {

  import OctopushSMS._

  private[this] implicit val executionContext = context.executionContext
  private[this] val octopush = new Octopush(userLogin, apiKey)(context.system.toClassic)
  val messageTitle = "Octopush"
  val log = context.log

  octopush.credit().onComplete {
    case Success(amount) => context.self ! Balance(amount)
    case Failure(e)      => context.self ! BalanceError(e)
  }

  override def onMessage(msg: SMSProtocol) = msg match {
    case SMSMessage(recipient, message: String) =>
      log.info("Sending SMS to {}: {}", recipient, message)
      val octopushSMSType = PhoneNumber.unwrap(recipient).take(3) match {
        case "+33" => Some(PremiumFrance)
        case "+32" => Some(WWW)
        case _ =>
          log.error("No Octopush SMS type defined for this phone number, not sending it: {}", recipient)
          None
      }
      octopushSMSType foreach { smsType =>
        val sms = SMS(smsRecipients = List(PhoneNumber.unwrap(recipient)), smsText = message, smsType = smsType, smsSender = sender, transactional = true)
        octopush.sms(sms).onComplete {
          case Success(result) => context.self ! SendOk(sms, result)
          case Failure(e)      => context.self ! SendError(sms, e)
        }
        Thread.sleep(10)
      }
      Behaviors.same

    case SendOk(sms, result) =>
      log.debug(
        "SMS to {} sent succesfully with {} SMS (cost: {}): {}",
        sms.smsRecipients.head, result.numberOfSendings, FormatUtils.formatEuros(result.cost), sms.smsText)
      context.self ! Balance(result.balance)
      Behaviors.same

    case SendError(sms, failure) =>
      log.error("SMS to {} ({}) failed", sms.smsRecipients.head, sms.smsText, failure)
      Alerts.sendAlert(messaging.Message(TextMessage, Severity.Error, s"Unable to send SMS to ${sms.smsRecipients.head}",
        s"${failure.getMessage}", icon = Some(Glyphs.telephoneReceiver)))
      Behaviors.same

    case Balance(balance) =>
      trackBalance(balance)
      Behaviors.same

    case BalanceError(failure) =>
      balanceError(failure)
      Behaviors.same
  }

}

object OctopushSMS {

  private case class SendOk(sms: SMS, result: SMSResult) extends SMSProtocol
  private case class SendError(sms: SMS, failure: Throwable) extends SMSProtocol

  def octopushSMS(userLogin: String, apiKey: String, sender: Option[String]): Behavior[SMSMessage] = Behaviors.setup[SMSProtocol] { context =>
    new OctopushSMS(context, userLogin, apiKey, sender)
  }.narrow

}

