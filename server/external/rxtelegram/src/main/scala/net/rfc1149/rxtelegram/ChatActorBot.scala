package net.rfc1149.rxtelegram

import akka.actor.{Actor, Stash}
import net.rfc1149.rxtelegram.Bot._
import net.rfc1149.rxtelegram.model._

trait ChatActorBot extends Actor with Stash {

  protected[this] var me: Option[User] = None
  protected[this] var chat: Option[Chat] = None

  def handleMessage(message: Message): Unit

  def handleOther(other: Any): Unit =
    context.parent.forward(other)

  def receive = {
    case (user: User, chat_id: Long) ⇒
      me = Some(user)
      context.become(receiveWork(To(chat_id)))
      unstashAll()
    case _ ⇒
      stash()
  }

  private[this] def receiveWork(target: Target): Receive = {
    case message: Message ⇒
      chat = chat.orElse(Some(message.chat))
      handleMessage(message)
    case action: Action ⇒
      context.parent.forward(Targetted(target, action))
    case other ⇒
      handleOther(other)
  }

}
