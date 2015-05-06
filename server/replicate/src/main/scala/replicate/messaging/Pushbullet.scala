package replicate.messaging

import akka.actor.Actor
import play.api.libs.json.{JsObject, JsValue, Json}
import replicate.utils.Global

import scalaj.http.{Http, HttpRequest}

class Pushbullet(bearerToken: String) extends Actor with Messaging {

  import Pushbullet._

  override def sendMessage(message: Message): Option[String] = {
    val basePayload = Json.obj("title" -> message.titleWithSeverity, "body" -> message.body)
    val payload = basePayload ++ message.url.fold(Json.obj("type" -> "note"))(l => Json.obj("type" -> "link", "url" -> l.toString))
    try {
      Some((post("/pushes", bearerToken, payload) \ "iden").as[String])
    } catch {
      case _: Throwable => sys.error(s"unable to send message")
    }
  }

  override def cancelMessage(identifier: String): Boolean = {
    delete(s"/pushes/$identifier", bearerToken)
    true
  }
}

object Pushbullet {

  private[this] def send(api: String, bearerToken: String, request: HttpRequest => HttpRequest): JsValue = {
    val response = request(Http(s"https://api.pushbullet.com/v2$api"))
      .header("Content-Type", "application/json")
      .header("Accept", "application/json")
      .header("Authorization", s"Bearer $bearerToken")
      .asString
    if (response.isSuccess)
      Json.parse(response.body).as[JsObject]
    else {
      Global.log.error(s"pushbullet result: ${Json.parse(response.body).as[JsObject]}")
      sys.error(s"unable to send pushbullet message")
    }
  }

  private[messaging] def post(api: String, bearerToken: String, payload: JsObject): JsValue =
    send(api, bearerToken, _.postData(payload.toString()))

  private[messaging] def delete(api: String, bearerToken: String): JsValue =
    send(api, bearerToken, _.method("DELETE"))

}

