package replicate.messaging

import akka.actor.Actor
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{HttpRequest, Uri}
import net.rfc1149.canape.Couch
import replicate.utils.Global

import scala.concurrent.Future

class FreeMobileSMS(user: String, password: String) extends Actor with Messaging {

  import Global._

  override def sendMessage(message: Message): Future[Option[String]] = {
    val request = HttpRequest().withUri(Uri("https://smsapi.free-mobile.fr/sendmsg").withQuery(Query("user" -> user, "pass" -> password, "msg" -> message.toString)))
    Http().singleRequest(request).map {
      case r if r.status.isSuccess() => None
      case r                         => throw new Couch.StatusError(r.status)
    } (dispatcher)
  }

}
