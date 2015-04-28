package replicate.messaging

import akka.http.scaladsl.model.HttpResponse
import akka.stream.scaladsl.Flow
import replicate.utils.Global

import scala.concurrent.Future
import scala.util.{Success, Try}

trait Messaging {

  implicit val dispatcher = Global.system.dispatchers.lookup("https-messaging-dispatcher")

  /**
   * Send a message to the intended recipient.
   *
   * @param message the message
   * @return a future which will complete to true if the delivery was successful, false otherwise
   */
  def sendMessage(message: String): Future[Boolean]

}