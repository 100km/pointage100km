package replicate.messaging

import akka.event.Logging
import replicate.messaging.Message.Severity
import replicate.utils.Global

import scala.concurrent.Future

object SystemLogger extends Messaging {

  override val officerId: String = "system"
  override val serviceName: String = "Logger"

  private[this] var log = Logging(Global.system, "messaging")

  override def sendMessage(message: Message): Future[Option[String]] = {
    val strMessage = s"${message.category}/${message.severity.toString.toLowerCase} $message"
    message.severity match {
      case Severity.Debug | Severity.Debug    => log.debug(strMessage )
      case Severity.Info  | Severity.Warning  => log.info(strMessage)
      case Severity.Error | Severity.Critical => log.warning(strMessage)
    }
    Future.successful(None)
  }

}
