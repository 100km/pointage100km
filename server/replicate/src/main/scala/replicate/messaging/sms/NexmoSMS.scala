package replicate.messaging.sms

import akka.NotUsed
import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{FormData, HttpResponse, MediaTypes, Uri}
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import net.rfc1149.canape.Couch
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsObject, JsPath, Reads}
import replicate.alerts.Alerts
import replicate.messaging.Message.{Severity, TextMessage}
import replicate.messaging.{Balance, BalanceError, BalanceTracker, Message ⇒ Msg}
import replicate.utils.{FormatUtils, Glyphs, Networks}

import scala.concurrent.Future

class NexmoSMS(senderId: String, apiKey: String, apiSecret: String) extends Actor with ActorLogging with BalanceTracker {

  import NexmoSMS._

  private[this] implicit val system = context.system
  private[this] implicit val executionContext = system.dispatcher
  private[this] implicit val materializer = ActorMaterializer.create(context)
  val messageTitle = "Nexmo"

  private[this] val apiPool = Http().cachedHostConnectionPoolHttps[NotUsed]("rest.nexmo.com")

  private[this] def sendSMS(recipient: String, message: String): Future[HttpResponse] = {
    val request = RequestBuilding.Post(
      SMSEndpoint,
      FormData("from" → senderId, "to" → recipient.stripPrefix("+"),
        "text" → message, "api_key" → apiKey, "api_secret" → apiSecret)).addHeader(Accept(MediaTypes.`application/json`))
    Source.single((request, NotUsed)).via(apiPool).runWith(Sink.head).map(_._1.get)
  }

  private[this] def checkBalance(): Future[HttpResponse] = {
    val uri = Uri(accountEndpoint + "get-balance").withQuery(Query("api_key" → apiKey, "api_secret" → apiSecret))
    Source.single((RequestBuilding.Get(uri), NotUsed)).via(apiPool).runWith(Sink.head).map(_._1.get)
  }

  override def preStart =
    log.debug("NexmoSMS service started")
  checkBalance().flatMap(Couch.checkResponse[JsObject]).map(js ⇒ (js \ "value").as[Double])
    .transform(Balance, BalanceError)
    .pipeTo(self)

  def receive = {
    case (recipient: String, message: String) ⇒
      sendSMS(recipient, message).flatMap(Couch.checkResponse[Response])
        .transform(DeliveryReport(recipient, message, _), DeliveryError(recipient, message, _))
        .pipeTo(self)

    case DeliveryReport(recipient, text, response) ⇒
      val parts = response.messageCount
      if (parts == 0)
        log.error("no message in response")
      else {
        var remaining = Double.MaxValue
        for ((message, idx) ← response.messages.zipWithIndex) {
          if (message.status == 0) {
            val network = message.network.flatMap(Networks.byMCCMNC.get).fold("unknown network")(op ⇒ s"${op.network} (${op.country})")
            val cost = message.messagePrice.fold("an unknown cost")(FormatUtils.formatEuros)
            val dst = message.to.fold("unknown recipient")('+' + _)
            val msg = Msg(TextMessage, Severity.Debug, s"Text message delivered to $dst (${idx + 1}/$parts)",
              s"Delivered through $network for $cost", icon = Some(Glyphs.telephoneReceiver))
            Alerts.sendAlert(msg)
            message.remainingBalance.foreach(b ⇒ remaining = remaining.min(b))
          } else {
            val errorMessage = message.errorText.fold(s"${message.status}")(explanation ⇒ s"${message.status}: $explanation")
            val msg = Msg(TextMessage, Severity.Error, s"Error when delivering text message to $recipient",
              s"$errorMessage (message was: $text)",
                          icon = Some(Glyphs.telephoneReceiver))
            Alerts.sendAlert(msg)
          }
        }
        if (remaining != Double.MaxValue)
          trackBalance(remaining)
      }

    case Failure(DeliveryError(recipient, text, t)) ⇒
      log.error(t, "Error when sending SMS to {} through Nexmo: {}", recipient, text)

    case Balance(balance) ⇒
      trackBalance(balance)

    case Failure(BalanceError(failure)) ⇒
      balanceError(failure)
  }

}

object NexmoSMS {

  private case class Message(_status: String, errorText: Option[String], _remainingBalance: Option[String],
      _messagePrice: Option[String], network: Option[String], to: Option[String]) {
    val status = _status.toInt
    val remainingBalance = _remainingBalance.map(_.toDouble)
    val messagePrice = _messagePrice.map(_.toDouble)
  }

  private implicit val messageReads: Reads[Message] = (
    (JsPath \ "status").read[String] and
    (JsPath \ "error-text").readNullable[String] and
    (JsPath \ "remaining-balance").readNullable[String] and
    (JsPath \ "message-price").readNullable[String] and
    (JsPath \ "network").readNullable[String] and
    (JsPath \ "to").readNullable[String])(Message.apply _)

  private case class Response(_messageCount: String, messages: List[Message]) {
    val messageCount = _messageCount.toInt
  }

  private implicit val responseReads: Reads[Response] =
    ((JsPath \ "message-count").read[String] and (JsPath \ "messages").read[List[Message]])(Response.apply _)

  private case class DeliveryReport(recipient: String, text: String, response: Response)
  private case class DeliveryError(recipient: String, text: String, throwable: Throwable) extends Exception

  private val SMSEndpoint = "/sms/json"
  private val accountEndpoint = "/account/"

}
