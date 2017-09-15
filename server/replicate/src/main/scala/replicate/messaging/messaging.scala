package replicate

import java.util.UUID

import akka.actor.{ Actor, ActorLogging }
import replicate.alerts.Alerts
import replicate.messaging.Message.Severity.Severity
import replicate.messaging.Message.{ Severity, TextMessage }
import replicate.utils.{ FormatUtils, Glyphs }

package object messaging {

  private[messaging] sealed trait Status
  private[messaging] case object Ok extends Status
  private[messaging] case object Notice extends Status
  private[messaging] case object Warning extends Status
  private[messaging] case object Critical extends Status

  private[messaging] val severities: Map[Status, Severity] =
    Map(Ok → Severity.Info, Notice → Severity.Info, Warning → Severity.Warning, Critical → Severity.Critical)

  private[messaging] def amountToStatus(amount: Double): (Status, Double) = {
    import replicate.utils.Global.TextMessages.TopUp._
    if (amount < criticalAmount)
      (Critical, criticalAmount)
    else if (amount < warningAmount)
      (Warning, warningAmount)
    else if (amount < noticeAmount)
      (Notice, noticeAmount)
    else
      (Ok, 0)
  }

  private[messaging] trait BalanceTracker extends Actor with ActorLogging {

    val messageTitle: String
    private[this] var currentStatus: Status = null
    private[this] var latestAlert: Option[UUID] = None

    private val icon: Some[String] = Some(Glyphs.telephoneReceiver)

    def trackBalance(balance: Double) = {
      val (status, limit) = amountToStatus(balance)
      log.debug("Balance for {} is {}", messageTitle, FormatUtils.formatEuros(balance))
      if (status != currentStatus) {
        val current = FormatUtils.formatEuros(balance)
        val message = (currentStatus, status) match {
          case (null, Ok) ⇒ s"Current balance is $current"
          case (_, Ok) ⇒ s"Balance has been restored to $current"
          case _ ⇒ s"Balance is $current, below the limit of ${FormatUtils.formatEuros(limit)}"
        }
        currentStatus = status
        latestAlert.foreach(Alerts.cancelAlert)
        latestAlert = Some(Alerts.sendAlert(Message(TextMessage, severities(status), messageTitle, message, icon = icon)))
      }
    }

    def balanceError(failure: Throwable) = {
      log.error(failure, "Unable to get balance for {}", messageTitle)
      latestAlert.foreach(Alerts.cancelAlert)
      latestAlert = Some(Alerts.sendAlert(Message(TextMessage, Severity.Critical, messageTitle, s"Unable to get balance", icon = icon)))
    }

  }

  case class Balance(balance: Double)
  case class BalanceError(failure: Throwable) extends Exception(failure)

}
