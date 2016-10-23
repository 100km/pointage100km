package net.rfc1149.rxtelegram

import akka.actor.{Actor, ActorLogging}
import net.rfc1149.rxtelegram.Bot._
import net.rfc1149.rxtelegram.model._

trait ChatActorBot extends Actor with ActorLogging {

  protected[this] var me: User = _
  protected[this] var chat: Chat = null
  protected[this] var target: To = _

  def ready_to_send(): Unit = {}
  def ready(): Unit = {}

  def handleMessage(message: Message): Unit

  def handleOther(other: Any): Unit =
    context.parent.forward(other)

  def receive = {
    case (user: User, chat_id: Long) ⇒
      me = user
      target = To(chat_id)
      try {
        ready_to_send()
      } catch {
        case t: Throwable ⇒
          log.error(t, "receiving chat information")
      }
    case message: Message ⇒
      if (chat == null) {
        chat = message.chat
        try {
          ready()
        } catch {
          case t: Throwable ⇒
            log.error(t, "receiving initial message from peer")
        }
      }
      try {
        handleMessage(message)
      } catch {
        case t: Throwable ⇒
          log.error(t, "handling message {}", message)
      }
    case action: Action ⇒
      context.parent.forward(Targetted(target, action))
    case other ⇒
      try {
        handleOther(other)
      } catch {
        case t: Throwable ⇒
          log.error(t, "handling other data {}", other)
      }
  }

}

