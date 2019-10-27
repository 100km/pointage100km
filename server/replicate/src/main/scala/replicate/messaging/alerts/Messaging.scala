package replicate.messaging.alerts

import akka.actor.typed._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import replicate.messaging.Message
import replicate.utils.Global

import scala.concurrent.Future
import scala.util.Try

abstract class Messaging extends ExtensibleBehavior[Messaging.Protocol] {

  import Messaging._

  import Global.dispatcher

  override def receive(context: TypedActorContext[Protocol], msg: Protocol): Behavior[Protocol] = msg match {
    case Deliver(message, token, respondTo) =>
      sendMessage(context.asScala, message).onComplete(r => respondTo ! DeliveryReceipt(r, token, context.asScala.self))
      Behaviors.same
    case Cancel(cancellationId) =>
      cancelMessage(context.asScala, cancellationId)
      Behaviors.same
  }

  override def receiveSignal(context: TypedActorContext[Protocol], msg: Signal): Behavior[Protocol] = Behaviors.same

  /**
   * Send a message to the intended recipient.
   *
   * @param context this actor context
   * @param message the message to send
   * @return a future which will complete to an optional string allowing to cancel the delivery
   *         if it has been succesful, or a failure otherwise
   */
  def sendMessage(context: ActorContext[Protocol], message: Message): Future[Option[String]]

  /**
   * Cancel a previously sent message.
   *
   * @param context this actor context
   * @param identifier the identifier returned by [[sendMessage]]
   * @return a future which completes into the success value of the cancellation
   */
  def cancelMessage(context: ActorContext[Protocol], identifier: String): Future[Boolean] =
    sys.error("Current backend does not support message cancellation")

}

object Messaging {

  sealed trait Protocol
  private[alerts] case class Deliver(message: Message, token: String, respondTo: ActorRef[DeliveryReceipt]) extends Protocol
  private[alerts] case class Cancel(cancellationId: String) extends Protocol

  private[alerts] case class DeliveryReceipt(result: Try[Option[String]], token: String, deliveredBy: ActorRef[Protocol])
}
