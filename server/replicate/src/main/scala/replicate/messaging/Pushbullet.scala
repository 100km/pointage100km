package replicate.messaging

import akka.actor.Actor
import play.api.libs.json.{JsObject, JsValue, Json}
import replicate.utils.Global

import scala.concurrent.Future
import scalaj.http.{Http, HttpRequest}

class Pushbullet(override val officerId: String, bearerToken: String) extends Actor with Messaging {

  import Pushbullet._

  override def sendMessage(message: Message): Future[Option[String]] = {
    val basePayload = Json.obj("title" -> message.titleWithSeverity, "body" -> message.body)
    val payload = basePayload ++ message.url.fold(Json.obj("type" -> "note"))(l => Json.obj("type" -> "link", "url" -> l.toString))
    push(bearerToken, payload)
      .transform(j => Some((j \ "iden").as[String]), _ => new RuntimeException(s"""unable to send message "$message" to $officerId"""))
  }

  override def cancelMessage(identifier: String): Future[Boolean] =
    delete(s"/pushes/$identifier", bearerToken).map(_ => true)
}

object Pushbullet {

  private[this] def send(api: String, bearerToken: String, request: HttpRequest => HttpRequest): Future[JsValue] = {
    Future {
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
    } (Global.httpsDdispatcher)
  }

  private[messaging] def post(api: String, bearerToken: String, payload: JsObject): Future[JsValue] =
    send(api, bearerToken, _.postData(payload.toString()))

  private[messaging] def push(bearerToken: String, payload: JsObject): Future[JsValue] =
    post("/pushes", bearerToken, payload)

  private[messaging] def delete(api: String, bearerToken: String): Future[JsValue] =
    send(api, bearerToken, _.method("DELETE"))

}

