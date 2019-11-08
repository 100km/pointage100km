package replicate.messaging.alerts

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.{ActorLogging, Props, Scheduler}
import akka.util.Timeout
import net.rfc1149.rxtelegram.Bot.{ActionDeleteMessage, ActionMessage, ParseModeMarkdown, RedirectedCommand, Targetted, To}
import net.rfc1149.rxtelegram.{BotActor, Options, model}
import replicate.messaging.Message
import replicate.messaging.alerts.Messaging.Protocol
import replicate.utils.Global

import scala.concurrent.Future
import scala.concurrent.duration._

class Telegram(id: String) extends Messaging {

  override def sendMessage(context: ActorContext[Protocol], message: Message): Future[Option[String]] = {
    val mdUrl = message.url.fold("")(uri => s" [(link)]($uri)")
    val mdMsg = message.severityOrMessageIcon.fold("")(_ + ' ') + s"*[${message.title}]* ${message.body} $mdUrl"
    implicit val scheduler: Scheduler = context.system.scheduler
    import context.executionContext
    implicit val timeout: Timeout = 5.seconds
    Telegram.bot.toTyped[RedirectedCommand].ask[model.Message](ref =>
      Targetted(To(id), ActionMessage(mdMsg, parse_mode = ParseModeMarkdown)).redirectResponseTo(ref.toUntyped))
      .map(answer => Some(answer.message_id.toString))
  }

  override def cancelMessage(context: ActorContext[Protocol], identifier: String): Future[Boolean] = {
    implicit val scheduler: Scheduler = context.system.scheduler
    implicit val timeout: Timeout = 5.seconds
    Telegram.bot.toTyped[RedirectedCommand].ask[Boolean] { ref =>
      Targetted(To(id), ActionDeleteMessage(identifier.toLong)).redirectResponseTo(ref.toUntyped)
    }
  }
}

object Telegram {

  private class TelegramBot extends BotActor(Global.replicateConfig.getString("telegram.token"), new Options(Global.replicateConfig)) with ActorLogging {

    override protected[this] def handleMessage(message: model.Message): Unit = {
      message.from.fold(log.info("received unknown message without sender")) { from =>
        log.info("received unknown message from {} ({})", from.fullName, from.id)
      }
    }
  }

  private val bot = Global.system.actorOf(Props[TelegramBot], "Telegram")
}
