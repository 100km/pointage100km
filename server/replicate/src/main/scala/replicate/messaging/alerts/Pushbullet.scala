package replicate.messaging.alerts

import akka.NotUsed
import akka.actor.Actor
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, MediaTypes, RequestEntity}
import akka.stream.scaladsl.{Sink, Source}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import net.rfc1149.canape.Couch
import play.api.libs.json.{JsObject, Json}
import replicate.messaging.Message
import replicate.utils.Global

import scala.concurrent.Future

class Pushbullet(bearerToken: String) extends Actor with Messaging {

  import Pushbullet._

  override def sendMessage(message: Message): Future[Option[String]] = {
    val body = message.severityOrMessageIcon.fold("")(_ + ' ') + message.body
    val basePayload = Json.obj("title" → message.titleWithSeverity, "body" → body)
    val payload = basePayload ++ message.url.fold(Json.obj("type" → "note"))(l ⇒ Json.obj("type" → "link", "url" → l.toString))
    post("/pushes", bearerToken, payload).map(js ⇒ Some((js \ "iden").as[String]))
  }

  override def cancelMessage(identifier: String): Future[Boolean] =
    delete(s"/pushes/$identifier", bearerToken).map(_ ⇒ true)
}

object Pushbullet extends PlayJsonSupport {

  import Global._

  private[this] val apiPool = Http().cachedHostConnectionPoolHttps[NotUsed]("api.pushbullet.com")

  private[this] def send(api: String, bearerToken: String, request: HttpRequest ⇒ HttpRequest): Future[JsObject] = {
    val partialRequest = HttpRequest().withUri(s"/v2$api")
      .withHeaders(List(`Accept`(MediaTypes.`application/json`), `Authorization`(OAuth2BearerToken(bearerToken))))
    Source.single((request(partialRequest), NotUsed)).via(apiPool).runWith(Sink.head).map(_._1.get).flatMap(Couch.checkResponse[JsObject])
  }

  private[messaging] def post(api: String, bearerToken: String, payload: JsObject): Future[JsObject] =
    Marshal(payload).to[RequestEntity].flatMap(e ⇒ send(api, bearerToken, _.withMethod(HttpMethods.POST).withEntity(e)))

  private[messaging] def delete(api: String, bearerToken: String): Future[JsObject] =
    send(api, bearerToken, _.withMethod(HttpMethods.DELETE))

}

