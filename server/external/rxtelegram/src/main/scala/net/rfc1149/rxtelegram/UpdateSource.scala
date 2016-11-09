package net.rfc1149.rxtelegram

import akka.actor.{Actor, ActorLogging, ActorRefFactory, Props}
import akka.http.scaladsl.util.FastFuture
import akka.pattern.{Backoff, BackoffSupervisor, pipe}
import akka.stream.scaladsl.{Source, SourceQueue, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import net.rfc1149.rxtelegram.model._

class UpdateSource(val token: String, val options: Options, val queue: SourceQueue[Update]) extends Actor with ActorLogging with Bot {

  import UpdateSource._

  implicit val actorSystem = context.system
  implicit val fm = ActorMaterializer.create(context)
  implicit val ec = context.dispatcher

  override def preStart = {
    super.preStart()
    self ! Request(None)
  }

  override def receive = {

    case Request(toAcknowledge) ⇒
      toAcknowledge.foreach(acknowledgeUpdate)
      getUpdates(limit = options.updatesBatchSize, timeout = options.longPollingDelay).transform(Updates, UpdateError).pipeTo(self)

    case Updates(updates) ⇒
      updates.foldLeft(FastFuture.successful[QueueOfferResult](QueueOfferResult.Enqueued)) {
        case (qor, update) ⇒
          qor flatMap {
            case QueueOfferResult.Enqueued | QueueOfferResult.Dropped ⇒
              queue.offer(update)
            case QueueOfferResult.QueueClosed | QueueOfferResult.Failure(_) ⇒
              context.stop(self)
              qor
          }
      }.andThen { case _ ⇒ self ! Request(updates.lastOption) }

    case UpdateError(throwable) ⇒
      log.error(throwable, "could not get updates")
      throw throwable
  }

}

object UpdateSource {

  def apply(token: String, options: Options)(implicit af: ActorRefFactory): Source[Update, SourceQueueWithComplete[Update]] = {
    Source.queue[Update](16, OverflowStrategy.backpressure).mapMaterializedValue { sourceQueue ⇒
      val updateSourceProps = Props(new UpdateSource(token, options, sourceQueue))
      val backoffSupervisorProps = BackoffSupervisor.props(Backoff.onFailure(updateSourceProps, "update-source",
        options.httpMinErrorRetryDelay, options.httpMaxErrorRetryDelay, 0.2))
      af.actorOf(backoffSupervisorProps)
      sourceQueue
    }
  }

  private case class Request(toAcknowledge: Option[Update])
  private case class Updates(updates: List[Update])
  private case class UpdateError(throwable: Throwable) extends Exception

}

