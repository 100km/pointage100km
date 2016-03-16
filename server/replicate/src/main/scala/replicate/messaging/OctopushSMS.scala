package replicate.messaging

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging}
import akka.pattern.pipe
import net.rfc1149.octopush.Octopush
import net.rfc1149.octopush.Octopush.{PremiumFrance, SMS, SMSResult}
import replicate.alerts.Alerts
import replicate.messaging.Message.{Severity, TextMessage}
import replicate.utils.Glyphs

class OctopushSMS(userLogin: String, apiKey: String, sender: Option[String]) extends Actor with ActorLogging with BalanceTracker {

  import OctopushSMS._

  private[this] implicit val dispatcher = context.system.dispatcher
  private[this] val octopush = new Octopush(userLogin, apiKey)(context.system)
  private[this] var currentStatus: Status = null
  val messageTitle = "Octopush"

  override def preStart =
    pipe(octopush.credit().transform(Balance, BalanceError)).to(self)

  def receive = {
    case (recipient: String, message: String) =>
      log.info("Sending SMS to {}: {}", recipient, message)
      val sms = SMS(smsRecipients = List(recipient), smsText = message, smsType = PremiumFrance, smsSender = sender, transactional = true)
      pipe(octopush.sms(sms).transform(SendOk(sms, _), SendError(sms, _))).to(self)

    case SendOk(sms, result) =>
      log.debug("SMS to {} sent succesfully with {} SMS (cost: {}): {}"  ,
        sms.smsRecipients.head, result.numberOfSendings, "%.2f€".format(result.cost), sms.smsText)
      self ! Balance(result.balance)

    case Failure(SendError(sms, failure)) =>
      log.error(failure, "SMS to {} ({}) failed", sms.smsRecipients.head, sms.smsText)
      Alerts.sendAlert(Message(TextMessage, Severity.Error, s"Unable to send SMS to ${sms.smsRecipients.head}",
        s"${failure.getMessage}", icon = Some(Glyphs.telephoneReceiver)))

    case Balance(balance) =>
      trackBalance(balance)

    case Failure(BalanceError(failure)) =>
      balanceError(failure)
  }

}

object OctopushSMS {

  private case class SendOk(sms: SMS, result: SMSResult)
  private case class SendError(sms: SMS, failure: Throwable) extends Exception(failure)

}

