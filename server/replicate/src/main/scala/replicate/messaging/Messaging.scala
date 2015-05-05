package replicate.messaging

import akka.actor.Actor
import akka.pattern.pipe
import replicate.utils.Global

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait Messaging { this: Actor =>

  implicit val dispatcher = Global.dispatcher

  override val receive: Receive = {
    case ('send, message: Message) =>
      sendMessage(message) map(Success(_)) recover { case t => Failure(t) } map((officerId, _)) pipeTo sender()
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
  def sendMessage(message: Message): Future[Option[String]]

  /**
   * Cancel a previously sent message.
   *
   * @param identifier the identifier returned by [[sendMessage]]
   * @return a future which completes into the success value of the cancellation
   */
  def cancelMessage(identifier: String): Future[Boolean] =
    sys.error("Current backend does not support message cancellation")

  /**
   * Unique id of the officer
   */
  def officerId: String

}
