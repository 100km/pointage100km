package replicate.messaging.alerts

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import replicate.messaging.Message
import replicate.utils.Global

import scala.concurrent.Future
import scala.util.Try

abstract class Messaging(context: ActorContext[Messaging.Protocol]) extends AbstractBehavior[Messaging.Protocol] {

  import Messaging._

  implicit val dispatcher = Global.dispatcher

  override def onMessage(msg: Protocol) = msg match {
    case Deliver(message, token, respondTo) ⇒
      sendMessage(message).onComplete(r ⇒ respondTo ! DeliveryReceipt(r, token, context.self))
      Behaviors.same
    case Cancel(cancellationId) ⇒
      cancelMessage(cancellationId)
      Behaviors.same
  }

  /**
   * Send a message to the intended recipient.
   *
   * @param message the message to send
   * @return a future which will complete to an optional string allowing to cancel the delivery
   *         if it has been succesful, or a failure otherwise
   */
  def sendMessage(message: Message): Future[Option[String]]

  /**
   * Cancel a previously sent message.
   *
   * @param identifier the identifier returned by [[sendMessage]]
   * @return a future which completes into the success value of the cancellation
   */
  def cancelMessage(identifier: String): Future[Boolean] =
    sys.error("Current backend does not support message cancellation")

}

object Messaging {

  sealed trait Protocol
  private[alerts] case class Deliver(message: Message, token: String, respondTo: ActorRef[DeliveryReceipt]) extends Protocol
  private[alerts] case class Cancel(cancellationId: String) extends Protocol

  private[alerts] case class DeliveryReceipt(result: Try[Option[String]], token: String, deliveredBy: ActorRef[Protocol])
}
