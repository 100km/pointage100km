package net.rfc1149.rxtelegram

import akka.actor.Status.Failure
import akka.actor.{ActorLogging, ActorRef, ActorSystem, Cancellable, Props}
import akka.pattern.pipe
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._
import net.rfc1149.rxtelegram.UpdateSource.{Reconnect, UpdateError, Updates}
import net.rfc1149.rxtelegram.model._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class UpdateSource(val token: String, val config: Config = ConfigFactory.load()) extends ActorPublisher[Update] with ActorLogging with Bot {

  implicit val actorSystem: ActorSystem = context.system
  implicit val ec: ExecutionContext = context.dispatcher
  implicit val fm: Materializer = ActorMaterializer()

  private[this] val httpErrorRetryDelay = config.as[FiniteDuration]("rxtelegram.http-error-retry-delay")
  private[this] val longPollingDelay = config.as[FiniteDuration]("rxtelegram.long-polling-delay")
  private[this] var scheduledRetry: Option[Cancellable] = None

  /**
   * `true` if a connection is either in progress or has been scheduled. `false` when there is no connection
   * because of an absent demand.
   */
  private[this] var ongoingConnection = false

  private[this] def connect() = {
    ongoingConnection = true
    assert(totalDemand > 0, "unneeded connection attempt to get update")
    getUpdates(limit = totalDemand, timeout = longPollingDelay).transform(Updates, UpdateError).pipeTo(self)
  }

  override def receive = {
    case Request(_) ⇒
      getMe.pipeTo(self)
      if (!ongoingConnection)
        connect()

    case Cancel ⇒
      scheduledRetry.foreach(_.cancel())
      scheduledRetry = None
      context.stop(self)

    case Updates(updates) ⇒
      assert(updates.size <= totalDemand, "too many updates received")
      updates.foreach { update ⇒
        onNext(update)
        acknowledgeUpdate(update)
      }
      if (totalDemand > 0)
        connect()
      else
        ongoingConnection = false

    case Failure(UpdateError(throwable)) ⇒
      log.error(throwable, "error when getting updates")
      scheduledRetry = Some(context.system.scheduler.scheduleOnce(httpErrorRetryDelay, self, Reconnect))

    case Reconnect ⇒
      // Only connect if the scheduled retry has not been cancelled in the meantime
      scheduledRetry.foreach(_ ⇒ connect())
      scheduledRetry = None
  }

}

object UpdateSource {

  def apply(token: String, config: Config = ConfigFactory.load()): Source[Update, ActorRef] =
    Source.actorPublisher(Props(new UpdateSource(token, config))).named("rxtelegram.UpdateSource")

  private case class Updates(updates: List[Update])
  private case class UpdateError(throwable: Throwable) extends Exception
  private case object Reconnect

}

