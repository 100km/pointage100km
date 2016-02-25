package replicate.messaging

import akka.actor.Actor
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, MediaTypes}
import net.rfc1149.canape.Couch
import play.api.libs.json.{JsObject, Json}
import replicate.utils.Global

import scala.concurrent.Future

class Pushbullet(bearerToken: String) extends Actor with Messaging {

  import Pushbullet._

  override def sendMessage(message: Message): Future[Option[String]] = {
    val body = message.severityOrMessageIcon.fold("")(_ + ' ') + message.body
    val basePayload = Json.obj("title" -> message.titleWithSeverity, "body" -> body)
    val payload = basePayload ++ message.url.fold(Json.obj("type" -> "note"))(l => Json.obj("type" -> "link", "url" -> l.toString))
    post("/pushes", bearerToken, payload).map(js => Some((js \ "iden").as[String]))
  }

  override def cancelMessage(identifier: String): Future[Boolean] =
    delete(s"/pushes/$identifier", bearerToken).map(_ => true)
}

object Pushbullet {

  import Global._

  private[this] def send(api: String, bearerToken: String, request: HttpRequest => HttpRequest): Future[JsObject] = {
    val partialRequest = HttpRequest().withUri(s"https://api.pushbullet.com/v2$api")
      .withHeaders(List(`Accept`(MediaTypes.`application/json`), `Authorization`(OAuth2BearerToken(bearerToken))))
    Http().singleRequest(request(partialRequest)).flatMap(Couch.checkResponse[JsObject])
  }

  private[messaging] def post(api: String, bearerToken: String, payload: JsObject): Future[JsObject] =
    send(api, bearerToken, _.withMethod(HttpMethods.POST).withEntity(Couch.jsonToEntity(payload)))

  private[messaging] def delete(api: String, bearerToken: String): Future[JsObject] =
    send(api, bearerToken, _.withMethod(HttpMethods.DELETE))

}
