package net.rfc1149.rxtelegram

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{RestartSource, Source}
import net.rfc1149.rxtelegram.model._

import scala.concurrent.Future

object UpdateSource {

  private class UpdatesIterator(val token: String, val options: Options)(implicit val actorSystem: ActorSystem) extends Bot with Iterator[Future[List[Update]]] {
    implicit val ec = actorSystem.dispatcher
    implicit val fm = ActorMaterializer.create(actorSystem)
    override def hasNext: Boolean = true
    override def next(): Future[List[Update]] =
      getUpdates(limit   = options.updatesBatchSize, timeout = options.longPollingDelay)
        .map { l ⇒ l.lastOption.foreach(acknowledgeUpdate); l }
  }

  private def source(token: String, options: Options)(implicit system: ActorSystem): Source[Update, NotUsed] =
    Source.fromIterator(() ⇒ new UpdatesIterator(token, options))
      .mapAsync(parallelism = 1)(identity)
      .mapConcat(identity)

  def apply(token: String, options: Options)(implicit system: ActorSystem): Source[Update, NotUsed] =
    RestartSource.withBackoff(minBackoff   = options.httpMinErrorRetryDelay, maxBackoff = options.httpMaxErrorRetryDelay, randomFactor = 0.2) {
      () ⇒ source(token, options)
    }

}

