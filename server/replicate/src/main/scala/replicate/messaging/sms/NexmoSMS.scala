package replicate.messaging.sms

import akka.NotUsed
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{FormData, HttpResponse, MediaTypes, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import net.rfc1149.canape.Couch
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsObject, JsPath, Reads}
import replicate.alerts.Alerts
import replicate.messaging.Message
import replicate.messaging.Message.{Severity, TextMessage}
import replicate.utils.Types.PhoneNumber
import replicate.utils.{FormatUtils, Glyphs, Networks}
import scalaz.@@

import scala.concurrent.Future
import scala.util.{Failure, Success}

class NexmoSMS(context: ActorContext[SMSProtocol], senderId: String, apiKey: String, apiSecret: String) extends AbstractBehavior[SMSProtocol] with BalanceTracker {

  import NexmoSMS.{Message ⇒ _, _}

  private[this] implicit val system = context.system.toUntyped
  private[this] implicit val executionContext = context.executionContext
  private[this] implicit val materializer = ActorMaterializer()(context.toUntyped)
  val messageTitle = "Nexmo"
  val log = context.log

  private[this] val apiPool = Http().cachedHostConnectionPoolHttps[NotUsed]("rest.nexmo.com")

  private[this] def sendSMS(recipient: String @@ PhoneNumber, message: String): Future[HttpResponse] = {
    val request = RequestBuilding.Post(
      SMSEndpoint,
      FormData("from" → senderId, "to" → PhoneNumber.unwrap(recipient).stripPrefix("+"),
        "text" → message, "api_key" → apiKey, "api_secret" → apiSecret)).addHeader(Accept(MediaTypes.`application/json`))
    Source.single((request, NotUsed)).via(apiPool).runWith(Sink.head).map(_._1.get)
  }

  private[this] def checkBalance(): Future[HttpResponse] = {
    val uri = Uri(accountEndpoint + "get-balance").withQuery(Query("api_key" → apiKey, "api_secret" → apiSecret))
    Source.single((RequestBuilding.Get(uri), NotUsed)).via(apiPool).runWith(Sink.head).map(_._1.get)
  }

  log.debug("NexmoSMS service started")
  checkBalance().flatMap(Couch.checkResponse[JsObject]).map(js ⇒ (js \ "value").as[Double])
    .transform(Balance, BalanceError)
    .foreach(context.self ! _)

  override def onMessage(msg: SMSProtocol) = msg match {
    case SMSMessage(recipient, message) ⇒
      sendSMS(recipient, message).flatMap(Couch.checkResponse[Response]).onComplete {
        case Success(response) ⇒ context.self ! DeliveryReport(recipient, message, response)
        case Failure(e)        ⇒ context.self ! DeliveryError(recipient, message, e)
      }
      Behavior.same

    case DeliveryReport(recipient, text, response) ⇒
      val parts = response.messageCount
      if (parts == 0)
        context.log.error("no message in response")
      else {
        var remaining = Double.MaxValue
        for ((message, idx) ← response.messages.zipWithIndex) {
          if (message.status == 0) {
            val network = message.network.flatMap(Networks.byMCCMNC.get).fold("unknown network")(op ⇒ s"${op.network} (${op.country})")
            val cost = message.messagePrice.fold("an unknown cost")(FormatUtils.formatEuros)
            val dst = message.to.fold("unknown recipient")('+' + _)
            val msg = Message(TextMessage, Severity.Debug, s"Text message delivered to $dst (${idx + 1}/$parts)",
              s"Delivered through $network for $cost", icon = Some(Glyphs.telephoneReceiver))
            Alerts.sendAlert(msg)
            message.remainingBalance.foreach(b ⇒ remaining = remaining.min(b))
          } else {
            val errorMessage = message.errorText.fold(s"${message.status}")(explanation ⇒ s"${message.status}: $explanation")
            val msg = Message(TextMessage, Severity.Error, s"Error when delivering text message to $recipient",
              s"$errorMessage (message was: $text)",
                              icon = Some(Glyphs.telephoneReceiver))
            Alerts.sendAlert(msg)
          }
        }
        if (remaining != Double.MaxValue)
          trackBalance(remaining)
      }
      Behavior.same

    case DeliveryError(recipient, text, t) ⇒
      context.log.error(t, "Error when sending SMS to {} through Nexmo: {}", recipient, text)
      Behavior.same

    case Balance(balance) ⇒
      trackBalance(balance)
      Behavior.same

    case BalanceError(failure) ⇒
      balanceError(failure)
      Behavior.same
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

  private case class DeliveryReport(recipient: String @@ PhoneNumber, text: String, response: Response) extends SMSProtocol
  private case class DeliveryError(recipient: String @@ PhoneNumber, text: String, throwable: Throwable) extends SMSProtocol

  private val SMSEndpoint = "/sms/json"
  private val accountEndpoint = "/account/"

  def nexmoSMS(senderId: String, apiKey: String, apiSecret: String): Behavior[SMSMessage] = Behaviors.setup[SMSProtocol] { context ⇒
    new NexmoSMS(context, senderId, apiKey, apiSecret)
  }.narrow

}
