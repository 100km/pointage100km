import akka.actor.Props
import akka.dispatch.{Await, Future, Promise}
import akka.event.Logging
import akka.util.Duration
import akka.util.duration._
import net.liftweb.json._
import net.rfc1149.canape._

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

  private[this] def systematicFutures =
    Future.sequence(List(localToRemoteReplication, remoteToLocalReplication, pingWithCompaction))

  private[this] def dataFutures =
    Future.sequence(List(incompleteCheckpoints, conflictingCheckpoints))

  var changesOccurred: Boolean = false

  def handleMessage(m: Any) {
    changesOccurred = true
  }

  context.actorOf(Props(new ChangesActor(self, local, Some("bib_input/race-related"))),
    "changes")

  override def act() {
    Await.ready(systematicFutures, Duration.Inf)
    if (changesOccurred) {
      changesOccurred = false
      Await.ready(dataFutures, Duration.Inf)
    }
  }

}