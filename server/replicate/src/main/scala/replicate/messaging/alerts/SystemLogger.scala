package replicate.messaging.alerts

import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.util.FastFuture
import replicate.messaging.Message
import replicate.messaging.Message.Severity
import replicate.messaging.alerts.Messaging.Protocol

import scala.concurrent.Future

class SystemLogger extends Messaging {

  override def sendMessage(context: ActorContext[Protocol], message: Message): Future[Option[String]] = {
    val strMessage = s"${message.category}/${message.severity.toString.toLowerCase} $message"
    message.severity match {
      case Severity.Debug | Severity.Verbose |
        Severity.Info | Severity.Warning => context.log.info(strMessage)
      case Severity.Error | Severity.Critical => context.log.warning(strMessage)
    }
    FastFuture.successful(None)
  }

}
