package replicate.utils

import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

case class Agent[T](initialValue: T) {
  private[this] val system = Global.system
  private[this] implicit val scheduler = system.scheduler
  private[this] implicit val timeout: Timeout = 1.minute // This is virtually infinite

  private[this] sealed trait Command
  private[this] case class Alter(f: T => T, replyTo: ActorRef[T]) extends Command
  private[this] case class Get(replyTo: ActorRef[T]) extends Command

  private[this] def agent(value: T): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Alter(f, replyTo) =>
          val newValue = f(value)
          replyTo ! newValue
          agent(newValue)
        case Get(replyTo) =>
          replyTo ! value
          Behaviors.same
      }

    }

  private[this] val actor: ActorRef[Command] = system.spawnAnonymous(agent(initialValue))

  def future(): Future[T] = actor ? Get
  def alter(f: T => T): Future[T] = actor ? (a => Alter(f, a))
  def alter(v: T): Future[T] = alter(_ => v)
}