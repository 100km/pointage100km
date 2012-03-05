import akka.dispatch.{Await, Future, Promise}
import akka.event.Logging
import akka.util.Duration
import akka.util.duration._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._

import Global._

class Systematic(local: Database, remote: Database) extends PeriodicTask(30 seconds) with LoggingError {

  override val log = Logging(system, "systematic")

  private[this] def localToRemoteReplication =
    withError(local.replicateTo(remote,
				("continuous" -> true) ~
				("filter" -> "bib_input/to-replicate")).toFuture,
      "cannot replicate from local to remote")

  private[this] def remoteToLocalReplication =
    withError(local.replicateFrom(remote,
				  ("continuous" -> true) ~
				  ("filter" -> "bib_input/to-replicate")).toFuture,
      "cannot replicate from remote to local")

  private[this] var noCompactionSince: Int = 0

  private[this] def compaction = {
    noCompactionSince = (noCompactionSince + 1) % 4
    if (noCompactionSince == 0)
      local.compact().toFuture
    else
      Promise.successful(null)
  }

  private[this] def futures =
    Future.sequence(List(localToRemoteReplication, remoteToLocalReplication, compaction))

  override def act() {
    Await.ready(futures, Duration.Inf)
  }

}
