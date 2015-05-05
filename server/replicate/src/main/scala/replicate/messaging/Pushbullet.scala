package replicate.messaging

import akka.actor.Actor
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.concurrent.Future
import scalaj.http.{Http, HttpRequest}

class Pushbullet(override val officerId: String, bearerToken: String) extends Actor with Messaging {

  private[this] def send(api: String, request: HttpRequest => HttpRequest): Future[JsValue] = {
    Future {
      val response = request(Http(s"https://api.pushbullet.com/v2$api"))
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .header("Authorization", s"Bearer $bearerToken")
        .asString
      if (response.isSuccess)
        Json.parse(response.body).as[JsObject]
      else
        sys.error(s""""unable to send command to $officerId""")
    }
  }

  private[this] def post(api: String, payload: JsObject): Future[JsValue] =
    send(api, _.postData(payload.toString()))

  private[this] def delete(api: String): Future[JsValue] =
    send(api, _.method("DELETE"))

  override def sendMessage(message: Message): Future[Option[String]] = {
    val basePayload = Json.obj("title" -> message.titleWithSeverity, "body" -> message.body)
    val payload = basePayload ++ message.url.fold(Json.obj("type" -> "note"))(l => Json.obj("type" -> "link", "url" -> l.toString))
    post("/pushes", payload)
      .transform(j => Some((j \ "iden").as[String]), _ => new RuntimeException(s"""unable to send message "$message" to $officerId"""))
  }

  override def cancelMessage(identifier: String): Future[Boolean] =
    delete(s"/pushes/$identifier").map(_ => true)
}

