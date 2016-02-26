package replicate.utils

import akka.NotUsed
import akka.stream.scaladsl.Flow

import scala.concurrent.duration.{Deadline, FiniteDuration}

trait StreamUtils {

  /**
    * Filter the upstream elements so that one goes through only if a specified delay has
    * expired since the last element which went through.
    *
    * @param interSampleDelay the time during which upstream elements are ignored
    * @tparam T the type of stream elements
    * @return the filtered stream
    */
  def sample[T](interSampleDelay: FiniteDuration): Flow[T, T, NotUsed] =
    Flow[T].statefulMapConcat { () =>
      var nextSampleDate = Deadline.now
      def filter(t: T): List[T] = {
        if (Deadline.now < nextSampleDate)
          Nil
        else {
          nextSampleDate = Deadline.now + interSampleDelay
          List(t)
        }
      }
      filter
    }
}

object StreamUtils extends StreamUtils
