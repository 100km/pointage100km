import akka.event.LoggingAdapter
import scala.concurrent.{ExecutionContext, Future}

trait LoggingError {

  import Global.dispatcher

  val log: LoggingAdapter

  def withError[T](future: Future[T], message: String): Future[Any] = {
    future onFailure {
      case e: Exception => log.warning(message + ": " + e)
    }
    future
  }

}
