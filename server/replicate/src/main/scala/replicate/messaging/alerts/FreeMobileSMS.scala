package replicate.messaging.alerts

import akka.NotUsed
import akka.actor.Actor
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.stream.scaladsl.{Sink, Source}
import net.rfc1149.canape.Couch
import replicate.messaging.Message
import replicate.utils.Global

import scala.concurrent.Future

class FreeMobileSMS(user: String, password: String) extends Actor with Messaging {

  import Global._

  private[this] val apiPool = Http().cachedHostConnectionPoolHttps[NotUsed]("smsapi.free-mobile.fr")

  override def sendMessage(message: Message): Future[Option[String]] = {
    val request = HttpRequest().withUri(Uri("/sendmsg").withQuery(Query("user" → user, "pass" → password, "msg" → message.toString)))
    Source.single((request, NotUsed)).via(apiPool).runWith(Sink.head).map(_._1.get match {
      case r if r.status.isSuccess() ⇒ None
      case r                         ⇒ throw new Couch.StatusError(r.status)
    })(context.system.dispatcher)
  }

}
