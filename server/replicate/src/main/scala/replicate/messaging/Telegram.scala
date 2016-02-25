package replicate.messaging

import akka.actor.{Actor, ActorLogging, Props}
import net.rfc1149.rxtelegram.Bot.{ActionMessage, ParseModeMarkdown, Targetted, To}
import net.rfc1149.rxtelegram.{ActorBot, model}
import replicate.utils.Global

import scala.concurrent.Future

class Telegram(id: String) extends Actor with ActorLogging with Messaging {
  override def sendMessage(message: Message): Future[Option[String]] = {
    val mdUrl = message.url.fold("")(uri => s" [(link)]($uri)")
    val mdMsg = message.severityOrMessageIcon.fold("")(_ + ' ') + s"*[${message.title}]* ${message.body} $mdUrl"
    Telegram.bot ! Targetted(To(id), ActionMessage(mdMsg, parse_mode = ParseModeMarkdown))
    Future.successful(None)
  }
}

object Telegram {

  private class TelegramBot extends ActorBot(Global.replicateConfig.getString("telegram.token")) with ActorLogging {

    override protected[this] def handleMessage(message: model.Message) = {
      log.info(s"received unknown message from ${message.from.fullName} (${message.from.id})")
    }
  }

  private val bot = Global.system.actorOf(Props[TelegramBot], "Telegram")
}