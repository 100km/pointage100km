package replicate.messaging

import akka.actor.Actor
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{Uri, HttpRequest}
import replicate.utils.Global

import scala.concurrent.Future

class FreeMobileSMS(user: String, password: String) extends Actor with Messaging {

  import Global._

  override def sendMessage(message: Message): Future[Option[String]] = {
    val request = HttpRequest().withUri(Uri("https://smsapi.free-mobile.fr/sendmsg").withQuery("user" -> user, "pass" -> password, "msg" -> message.toString))
    Http().singleRequest(request).map {
      case r if r.status.isSuccess() => None
      case r                         => sys.error(r.status.reason())
    } (dispatcher)
  }

}
