package replicate.messaging

import akka.actor.Actor
import replicate.utils.Global

import scala.concurrent.Future

trait Messaging { this: Actor ⇒

  implicit val dispatcher = Global.dispatcher

  override val receive: Receive = {
    case ('deliver, message: Message, token) ⇒
      val s = sender()
      sendMessage(message).onComplete(r ⇒ s ! ('deliveryReceipt, r, token))
    case ('cancel, cancellationId: String) ⇒
      cancelMessage(cancellationId)
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

