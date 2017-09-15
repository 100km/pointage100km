package replicate.messaging

import akka.actor.{ Actor, ActorLogging, Props }
import akka.http.scaladsl.util.FastFuture
import net.rfc1149.rxtelegram.Bot.{ ActionMessage, ParseModeMarkdown, Targetted, To }
import net.rfc1149.rxtelegram.{ BotActor, Options, model }
import replicate.utils.Global

import scala.concurrent.Future

class Telegram(id: String) extends Actor with ActorLogging with Messaging {

  override def sendMessage(message: Message): Future[Option[String]] = {
    val mdUrl = message.url.fold("")(uri ⇒ s" [(link)]($uri)")
    val mdMsg = message.severityOrMessageIcon.fold("")(_ + ' ') + s"*[${message.title}]* ${message.body} $mdUrl"
    Telegram.bot ! Targetted(To(id), ActionMessage(mdMsg, parse_mode = ParseModeMarkdown))
    FastFuture.successful(None)
  }
}

object Telegram {

  private class TelegramBot extends BotActor(Global.replicateConfig.getString("telegram.token"), new Options(Global.replicateConfig)) with ActorLogging {

    override protected[this] def handleMessage(message: model.Message) = {
      message.from.fold(log.info("received unknown message without sender")) { from ⇒
        log.info("received unknown message from {} ({})", from.fullName, from.id)
      }
    }
  }

  private val bot = Global.system.actorOf(Props[TelegramBot], "Telegram")
}
