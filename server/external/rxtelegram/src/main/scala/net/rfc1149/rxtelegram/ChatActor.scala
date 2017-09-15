package net.rfc1149.rxtelegram

import akka.actor.{ Actor, Stash }
import net.rfc1149.rxtelegram.Bot._
import net.rfc1149.rxtelegram.BotActor.GetMe
import net.rfc1149.rxtelegram.model._

trait ChatActor extends Actor with Stash {

  protected[this] val chat_id: Long

  protected[this] var me: User = _
  protected[this] var chat: Option[Chat] = None
  private[this] val target: Target = To(chat_id)

  def handleMessage(message: Message): Unit

  def handleOther(other: Any): Unit =
    context.parent.forward(other)

  override def preStart() = {
    super.preStart()
    context.parent ! GetMe
  }

  override def receive = {
    case me: User ⇒
      this.me = me
      unstashAll()
      context.become(receivePermanent)

    case _ ⇒
      stash()
  }

  val receivePermanent: Receive = {
    case message: Message ⇒
      chat = chat.orElse(Some(message.chat))
      handleMessage(message)
    case action: Action ⇒
      context.parent.forward(Targetted(target, action))
    case other ⇒
      handleOther(other)
  }

}
