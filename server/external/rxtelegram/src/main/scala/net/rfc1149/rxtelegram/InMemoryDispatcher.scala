package net.rfc1149.rxtelegram

import akka.actor.ActorRef

trait InMemoryDispatcher extends ChatDispatcher {

  private[this] var selections: Map[Long, ActorRef] = Map()

  override def addChat(id: Long, actorSelection: ActorRef): Unit =
    selections += id → actorSelection

  override def removeChat(id: Long): Unit =
    selections -= id

  override def actorRef(id: Long): Option[ActorRef] =
    selections.get(id)

}
