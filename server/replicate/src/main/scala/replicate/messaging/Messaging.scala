package replicate.messaging

import akka.actor.Actor
import replicate.utils.Global

import scala.util.Try

trait Messaging { this: Actor =>

  implicit val dispatcher = Global.dispatcher

  override val receive: Receive = {
    case ('send, message: Message) =>
      sender() ! (officerId, Try(sendMessage(message)))
    case ('cancel, cancellationId: String) =>
      cancelMessage(cancellationId)
  }

  /**
   * Send a message to the intended recipient.
   *
   * @param message the message to send
   * @return a future which will complete to an optional string allowing to cancel the delivery
   *         if it has been succesful, or a failure otherwise
   */
  def sendMessage(message: Message): Option[String]

  /**
   * Cancel a previously sent message.
   *
   * @param identifier the identifier returned by [[sendMessage]]
   * @return a future which completes into the success value of the cancellation
   */
  def cancelMessage(identifier: String): Boolean =
    sys.error("Current backend does not support message cancellation")

  /**
   * Unique id of the officer
   */
  def officerId: String

}

