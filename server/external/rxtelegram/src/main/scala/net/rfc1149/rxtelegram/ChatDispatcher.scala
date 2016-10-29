package net.rfc1149.rxtelegram

import akka.actor.ActorRef

trait ChatDispatcher {

  def addChat(id: Long, actorSelection: ActorRef): Unit
  def removeChat(id: Long): Unit = {}
  def actorRef(id: Long): Option[ActorRef]

}
