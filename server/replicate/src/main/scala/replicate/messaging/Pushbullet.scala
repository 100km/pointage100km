package replicate.messaging

import java.io.ByteArrayOutputStream

import play.api.libs.json.{JsObject, JsValue, Json}
import sun.misc.BASE64Encoder

import scala.concurrent.Future
import scalaj.http.{Http, HttpRequest}

class Pushbullet(override val officerId: String, bearerToken: String) extends Messaging {

  import Pushbullet._

  override val serviceName = "Pushbullet"

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
        sys.error("unable to send message to $officerId")
    }
  }

  private[this] def post(api: String, payload: JsObject): Future[JsValue] =
    send(api, _.postData(payload.toString()))

  private[this] def delete(api: String): Future[JsValue] =
    send(api, _.method("DELETE"))

  override def sendMessage(title: String, body: String, url: Option[String] = None): Future[Option[String]] = {
    val basePayload = Json.obj("title" -> title, "body" -> body, "icon" -> base64icon)
    val payload = basePayload ++ url.fold(Json.obj("type" -> "note"))(l => Json.obj("type" -> "link", "url" -> l))
    post("/pushes", payload).map(j => Some((j \ "iden").as[String]))
  }

  override def dismissMessage(identifier: String): Future[Boolean] =
    post(s"/pushed/$identifier", Json.obj("dismissed" -> true)).map(_ => true)

  override def cancelMessage(identifier: String): Future[Boolean] =
    delete(s"/pushes/$identifier").map(_ => true)
}

object Pushbullet {

  val base64icon: String = {
    val in = getClass.getResourceAsStream("/pushbullet-icon.jpg")
    require(in != null, "Resource not found")
    val out = new ByteArrayOutputStream()
    new BASE64Encoder().encode(in, out)
    out.toString("UTF-8")
  }

}
