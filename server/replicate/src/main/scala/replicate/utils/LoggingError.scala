package replicate.utils

import akka.event.LoggingAdapter

import scala.concurrent.Future

trait LoggingError {

  import Global.dispatcher

  val log: LoggingAdapter

  def withError[T](future: Future[T], message: String): Future[Any] = {
    future.failed.foreach(log.error(_, message))
    future
  }

}
