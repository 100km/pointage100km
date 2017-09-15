package net.rfc1149.canape.utils

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}

object DelayedSource {

  implicit class Delayed[T, Mat](inner: Source[T, Mat]) {
    def delayConnection(delay: FiniteDuration)(implicit system: ActorSystem): Source[T, Future[Mat]] = {
      val p = Promise[Source[T, Mat]]
      system.scheduler.scheduleOnce(delay) { p.success(inner) }(system.dispatcher)
      Source.fromFutureSource(p.future)
    }
  }

}
