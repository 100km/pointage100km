import akka.actor.Props
import akka.dispatch.{Await, Future, Promise}
import akka.event.Logging
import akka.util.Duration
import akka.util.duration._
import net.liftweb.json._
import net.rfc1149.canape._

import Global._

class Systematic(local: Database, remote: Database) extends PeriodicActor(5 seconds) {

  override val log = Logging(context.system, this)

  private[this] def withError[T](future: Future[T], message: String): Future[Any] =
    future onFailure {
      case e: Exception => log.warning(message + ": " + e)
    }

  private[this] def localToRemoteReplication =
    withError(local.replicateTo(remote, true).toFuture,
      "cannot replicate from local to remote")

  private[this] def remoteToLocalReplication =
    withError(local.replicateFrom(remote, true).toFuture,
      "cannot replicate from remote to local")

  private[this] def ping =
    withError(Replicate.ping(local), "cannot ping database")

  private[this] var noCompactionSince: Int = 0

  private[this] def pingWithCompaction = {
    ping onSuccess {
      case _ =>
        noCompactionSince = (noCompactionSince + 1) % 1000
        if (noCompactionSince == 0)
          local.compact().toFuture
        else
          Promise.successful(null)
    }
  }

  private[this] def futures =
    Future.sequence(List(localToRemoteReplication, remoteToLocalReplication, pingWithCompaction))

  override def act() {
    Await.ready(futures, Duration.Inf)
  }

}
