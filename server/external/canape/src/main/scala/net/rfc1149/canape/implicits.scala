package net.rfc1149.canape

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.language.implicitConversions

object implicits {

  // TODO: remove after the transition phase
  implicit class CouchRequestEmulation[T](val f: Future[T]) extends AnyVal {
    @deprecated("data is already a future", "spray") def toFuture(): Future[T] = f
    def execute()(implicit timeout: Duration): T = Await.result(f, timeout)
  }

}
