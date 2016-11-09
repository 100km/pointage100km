package net.rfc1149.rxtelegram

import akka.actor.{ActorContext, ActorRef, Props}
import net.rfc1149.rxtelegram.model.{Chat, Message}

abstract class DispatcherBotActor(token: String, options: Options) extends BotActor(token, options) with ChatDispatcher {

  import DispatcherBotActor._

  def createActor(chat: Chat, message: Message, context: ActorContext): Option[ActorRef]

  private[this] def registerChatActor(chat_id: Long, actorRef: ActorRef): Unit = {
    actorRef ! (me, chat_id)
    addChat(chat_id, actorRef)
  }

  override def handleMessage(message: Message): Unit = {
    val chat = message.chat
    val chat_id = chat.id
    actorRef(chat_id).fold {
      for (actorRef ← createActor(chat, message, context)) {
        registerChatActor(chat_id, actorRef)
        actorRef ! message
      }
    } {
      _ ! message
    }
  }

  override def handleOther(other: Any): Unit = other match {
    case CreateChat(chat_id: Long, props: Props, name: String) ⇒
      registerChatActor(chat_id, context.actorOf(props, name))
    case RemoveChat(id) ⇒
      removeChat(id)
    case _ ⇒
      super.handleOther(other)
  }

}

object DispatcherBotActor {
  case class CreateChat(chat_id: Long, props: Props, name: String)
  case class RemoveChat(chat_id: Long)
}
