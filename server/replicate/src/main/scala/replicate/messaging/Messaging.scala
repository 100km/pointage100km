package replicate.messaging

import replicate.utils.Global

import scala.concurrent.Future

trait Messaging {

  implicit val dispatcher = Global.system.dispatchers.lookup("https-messaging-dispatcher")

  /**
   * Send a message to the intended recipient.
   *
   * @param title the message title
   * @param body the message body
   * @param url the messagee link if any
   * @return a future which will complete to an optional string allowing to cancel the delivery
   *         if it has been succesful, or a failure otherwise
   */
  def sendMessage(title: String, body: String, url: Option[String] = None): Future[Option[String]]

  /**
   * Dismiss a previously sent message.
   *
   * @param identifier the identifier returned by [[sendMessage]]
   * @return a future which completes into the success value of the cancellation
   */
  def dismissMessage(identifier: String): Future[Boolean] =
    sys.error("Current backend does not support message dismissal")

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

  /**
   * Identifier for the service
   */
  val serviceName: String

  override def toString = s"$serviceName($officerId)"

}
