package replicate.messaging

import akka.actor.{Actor, ActorLogging}
import replicate.messaging.Message.Severity

import scala.concurrent.Future

class SystemLogger extends Actor with Messaging with ActorLogging {

  override val officerId: String = "system"

  override def sendMessage(message: Message): Future[Option[String]] = {
    val strMessage = s"${message.category}/${message.severity.toString.toLowerCase} $message"
    message.severity match {
      case Severity.Debug | Severity.Verbose |
           Severity.Info  | Severity.Warning  => log.info(strMessage)
      case Severity.Error | Severity.Critical => log.warning(strMessage)
    }
    Future.successful(None)
  }

}
