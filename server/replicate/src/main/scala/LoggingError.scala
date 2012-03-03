import akka.dispatch.Future
import akka.event.LoggingAdapter

trait LoggingError {

  val log: LoggingAdapter

  def withError[T](future: Future[T], message: String): Future[Any] =
    future onFailure {
      case e: Exception => log.warning(message + ": " + e)
    }

}
