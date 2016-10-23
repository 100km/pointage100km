package net.rfc1149.rxtelegram

import akka.actor.{ActorContext, ActorRef, Props}
import net.rfc1149.rxtelegram.model.{Chat, Message}

abstract class DispatcherBot(token: String) extends ActorBot(token) with ChatDispatcher {

  import DispatcherBot._

  def createActor(chat: Chat, message: Message, context: ActorContext): Option[ActorRef]

  override def handleMessage(message: Message): Unit = {
    val chat = message.chat
    val chat_id = chat.id
    actorRef(chat_id).fold {
      for (actorRef ← createActor(chat, message, context)) {
        addChat(chat_id, actorRef)
        actorRef ! (me, chat_id)
        actorRef ! message
      }
    } {
      _ ! message
    }
  }

  override def handleOther(other: Any): Unit = other match {
    case CreateChat(chat_id: Long, props: Props, name: String) ⇒
      val actorRef = context.actorOf(props, name)
      addChat(chat_id, actorRef)
      actorRef ! (me, chat_id)
    case RemoveChat(id) ⇒
      removeChat(id)
    case _ ⇒
      super.handleOther(other)
  }

}

object DispatcherBot {

  case class RemoveChat(chat_id: Long)

  case class CreateChat(chat_id: Long, props: Props, name: String)

}
