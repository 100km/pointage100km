package net.rfc1149.octopush

import java.security.MessageDigest

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Post
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.unmarshalling.{ Unmarshal, Unmarshaller }
import akka.http.scaladsl.util.FastFuture
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Source }

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.{ Failure, Success }
import scala.xml.NodeSeq

class Octopush(userLogin: String, apiKey: String)(implicit system: ActorSystem) extends ScalaXmlSupport {

  import Octopush._

  private[this] implicit val materializer = ActorMaterializer()
  private[this] implicit val executionContext = system.dispatcher
  private[this] implicit val log = system.log
  private[this] val apiPool = Http().cachedHostConnectionPoolHttps[NotUsed]("www.octopush-dm.com")

  private[this] def apiRequest[T](path: String, fields: (String, String)*)(implicit ev: Unmarshaller[NodeSeq, T]): Future[T] = {
    val formData = FormData(Seq("user_login" → userLogin, "api_key" → apiKey) ++ fields: _*)
    val request = Post(s"/api/$path", formData).addHeader(Accept(MediaTypes.`application/xml`))
    log.debug("Posting {}", request)
    Source.single((request, NotUsed)).via(apiPool).runWith(Sink.head).map(_._1).flatMap {
      case Success(response) if response.status.isSuccess() ⇒
        Unmarshal(response.entity).to[NodeSeq].flatMap {
          x ⇒
            log.debug("Received succesful answer with payload {}", x)
            // Errors are delivered as 200 OK with a payload containing a non-zero error_code field
            (x \ "error_code").headOption.map(_.text.toInt).filterNot(_ == 0).fold(Unmarshal(x).to[T])(e ⇒ FastFuture.failed(APIError(e)))
        }
      case Success(response) ⇒
        log.debug("Received unsuccessful answer: {}", response.status)
        FastFuture.failed(new StatusError(response.status))
      case Failure(t) ⇒
        log.error(t, "Connexion failure")
        FastFuture.failed(t)
    }
  }

  def balance(): Future[Balance] = apiRequest("balance")(balanceUnmarshaller)

  def credit(): Future[Double] = apiRequest("credit")(creditUnmarshaller)

  def sms(sms: SMS): Future[SMSResult] = apiRequest("sms", sms.buildParameters.toSeq: _*)(smsResultUnmarshaller)

}

object Octopush {

  class StatusError(val status: StatusCode) extends Exception {
    override def getMessage = s"${status.intValue} ${status.reason}"
  }

  object StatusError {
    def unapply(statusError: StatusError): Option[Int] = Some(statusError.status.intValue())
  }

  case class APIError(errorCode: Int) extends Exception {
    override def getMessage = s"$errorCode ${ErrorCodes.errorMessage(errorCode)}"
  }

  case class SMS(smsRecipients: List[String], smsText: String,
    smsType: SmsType, smsSender: Option[String] = None, sendingTime: Option[DateTime] = None, sendingPeriod: Option[Duration] = None,
    recipientFirstNames: Option[List[String]] = None, recipientLastNames: Option[List[String]] = None,
    smsFields1: Option[List[String]] = None, smsFields2: Option[List[String]] = None, smsFields3: Option[List[String]] = None,
    simulation: Boolean = false, requestId: Option[String] = None, withReplies: Boolean = false, transactional: Boolean = false,
    msisdnSender: Boolean = false, requestKeys: String = "") {

    import SMS._

    def buildParameters: Map[String, String] = {
      var params: Map[String, String] = Map("sms_recipients" → smsRecipients.mkString(","), "sms_text" → smsText,
        "sms_type" → smsType.toString)
      smsSender.foreach(params += "sms_sender" → _)
      if (sendingTime.isDefined && sendingPeriod.isDefined)
        throw new IllegalArgumentException("only one of sendingTime and sendingPeriod can be defined")
      sendingTime.foreach(t ⇒ params += "sending_time" → (t.clicks / 1000).toString)
      sendingPeriod.foreach(params += "sending_period" → _.toSeconds.toString)
      recipientFirstNames.foreach(params += "recipient_first_names" → _.mkString(","))
      recipientLastNames.foreach(params += "recipient_last_names" → _.mkString(","))
      smsFields1.foreach(params += "sms_fields_1" → _.mkString(","))
      smsFields2.foreach(params += "sms_fields_2" → _.mkString(","))
      smsFields3.foreach(params += "sms_fields_3" → _.mkString(","))
      if (simulation)
        params += "request_mode" → "simu"
      requestId.foreach(params += "request_id" → _)
      if (withReplies)
        params += "with_replies" → "1"
      if (transactional)
        params += "transactional" → "1"
      if (msisdnSender)
        params += "msisdn_sender" → "1"
      if (requestKeys != "") {
        var str = ""
        for (c ← requestKeys)
          sha1keys.get(c) match {
            case Some(key) ⇒
              params.get(key) match {
                case Some(value) ⇒ str += value
                case None ⇒ throw new IllegalArgumentException(s"no value defined for key $key ($c)")
              }
            case None ⇒
              throw new IllegalArgumentException(s"unknown key $c")
          }
        params += "request_keys" → requestKeys
        val md = MessageDigest.getInstance("SHA-1")
        params += "request_sha1" → md.digest(str.getBytes("UTF-8")).map("%02x".format(_)).mkString
      }

      params
    }

  }

  object SMS {

    private val sha1keys = Map('T' → "sms_text", 'R' → "sms_recipients", 'M' → "sms_mode",
      'Y' → "sms_type", 'S' → "sms_sender", 'D' → "sending_date", 'a' → "recipients_first_names",
      'b' → "recipients_last_names", 'c' → "sms_fields_1", 'd' → "sms_fields_2", 'e' → "sms_fields_3",
      'W' → "with_replies", 'N' → "transactional", 'Q' → "request_id")
  }

  case class Balance(lowCostFrance: Double, premiumFrance: Double)

  case class SMSSuccess(recipient: String, countryCode: String, cost: Double)

  // XXX Find definition and include failures
  case class SMSResult(cost: Double, balance: Double,
    ticket: String, sendingDate: Long, numberOfSendings: Int,
    currencyCode: String, successes: Seq[SMSSuccess])

  val balanceUnmarshaller: Unmarshaller[NodeSeq, Balance] = Unmarshaller.strict { xml ⇒
    Balance(
      lowCostFrance = (xml \ "balance" filter (_ \@ "type" == "XXX")).text.toDouble,
      premiumFrance = (xml \ "balance" filter (_ \@ "type" == "FR")).text.toDouble)
  }

  val creditUnmarshaller: Unmarshaller[NodeSeq, Double] = Unmarshaller.strict { xml ⇒
    (xml \ "credit").text.toDouble
  }

  val smsResultUnmarshaller: Unmarshaller[NodeSeq, SMSResult] = Unmarshaller.strict { xml ⇒
    SMSResult(
      cost = (xml \ "cost").text.toDouble,
      balance = (xml \ "balance").text.toDouble,
      ticket = (xml \ "ticket").text,
      sendingDate = (xml \ "sending_date").text.toLong,
      numberOfSendings = (xml \ "number_of_sendings").text.toInt,
      currencyCode = (xml \ "currency_code").text,
      successes = (xml \ "successs" \ "success").map { success ⇒
        SMSSuccess(
          recipient = (success \ "recipient").text,
          countryCode = (success \ "country_code").text,
          cost = (success \ "cost").text.toDouble)
      })
  }

  sealed trait SmsType
  case object LowCostFrance extends SmsType {
    override val toString = "XXX"
  }
  case object PremiumFrance extends SmsType {
    override val toString = "FR"
  }
  case object WWW extends SmsType {
    override val toString = "WWW"
  }

}
