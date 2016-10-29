package net.rfc1149.rxtelegram

import akka.actor.Actor
import net.rfc1149.rxtelegram.Bot._
import net.rfc1149.rxtelegram.model._

trait ChatActorBot extends Actor {

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
      ready_to_send()

    case message: Message ⇒
      if (chat == null) {
        chat = message.chat
        ready()
      }
      handleMessage(message)

    case action: Action ⇒
      context.parent.forward(Targetted(target, action))

    case other ⇒
      handleOther(other)
  }

}
