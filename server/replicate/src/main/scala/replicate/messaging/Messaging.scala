package replicate.messaging

import replicate.utils.Global

import scala.concurrent.Future

trait Messaging {

  implicit val dispatcher = Global.system.dispatchers.lookup("https-messaging-dispatcher")

  /**
   * Send a message to the intended recipient.
   *
   * @param message the message
   * @return a future which will complete to true if the delivery was successful, false otherwise
   */
  def sendMessage(message: String): Future[Boolean]

  /**
   * Unique id of the officer
   */
  def officerId: String

  override def toString = s"<Messaging $officerId>"

}
