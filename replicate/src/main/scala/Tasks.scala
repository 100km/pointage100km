import akka.dispatch.{Await, Future, Promise}
import akka.event.Logging
import akka.util.Duration
import akka.util.duration._
import net.rfc1149.canape._

import FutureUtils._
import Global._

class Tasks(local: Database, remote: Database)
    extends PeriodicActor(5 seconds) with IncompleteCheckpoints with ConflictsSolver {

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
    ping flatMap {
      _ =>
        noCompactionSince = (noCompactionSince + 1) % 1000
        if (noCompactionSince == 0)
          local.compact().toFuture
        else
          Promise.successful(null)
    }
  }

  private[this] def incompleteCheckpoints =
    withError(fixIncompleteCheckpoints(local),
      "unable to get incomplete checkpoints")

  private[this] def conflictingCheckpoints =
    withError(fixConflictingCheckpoints(local),
      "unable to get conflicting checkpoints")

  private[this] def allFutures =
    Future.sequence(List(localToRemoteReplication,
      remoteToLocalReplication,
      pingWithCompaction,
      incompleteCheckpoints,
      conflictingCheckpoints))

  override def act() {
    Await.ready(allFutures, Duration.Inf)
  }

}