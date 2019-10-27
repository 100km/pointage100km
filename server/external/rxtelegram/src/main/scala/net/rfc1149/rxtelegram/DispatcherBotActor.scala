package net.rfc1149.rxtelegram

import akka.actor.{ActorContext, ActorRef, Props}
import net.rfc1149.rxtelegram.model.{Chat, Message}

abstract class DispatcherBotActor(token: String, options: Options) extends BotActor(token, options) with ChatDispatcher {

  import DispatcherBotActor._

  def createActor(chat: Chat, message: Message, context: ActorContext): Option[ActorRef]

  override def handleMessage(message: Message): Unit = {
    val chat = message.chat
    val chat_id = chat.id
    actorRef(chat_id).fold {
      for (actorRef <- createActor(chat, message, context)) {
        addChat(chat_id, actorRef)
        actorRef ! message
      }
    } {
      _ ! message
    }
  }

  override def handleOther(other: Any): Unit = other match {
    case CreateChat(chat_id, propsCreator, name) =>
      addChat(chat_id, context.actorOf(propsCreator(chat_id), name))
    case RemoveChat(id) =>
      removeChat(id)
    case _ =>
      super.handleOther(other)
  }

}

object DispatcherBotActor {
  case class CreateChat(chat_id: Long, propsCreator: Long => Props, name: String)
  case class RemoveChat(chat_id: Long)
}

