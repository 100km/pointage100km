import akka.dispatch.{Await, Future, Promise}
import akka.event.Logging
import akka.util.Duration
import akka.util.duration._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._

import Global._

class Systematic(local: Database, remote: Option[Database]) extends PeriodicTask(30 seconds) with LoggingError {

  override val log = Logging(system, "systematic")

  private[this] def localToRemoteReplication =
    if (Replicate.options.replicate)
      withError(local.replicateTo(remote.get, Systematic.replicateOptions).toFuture,
		"cannot replicate from local to remote")
    else
      Promise.successful(null)

  private[this] def remoteToLocalReplication =
    if (Replicate.options.replicate)
      withError(local.replicateFrom(remote.get, Systematic.replicateOptions).toFuture,
		"cannot replicate from remote to local")
    else
      Promise.successful(null)

  private[this] var noCompactionSince: Int = 0

  private[this] def compaction = {
    noCompactionSince = (noCompactionSince + 1) % 4
    if (noCompactionSince == 0 && Replicate.options.compact)
      local.compact().toFuture
    else
      Promise.successful(null)
  }

  private[this] def futures =
    Future.sequence(List(localToRemoteReplication, remoteToLocalReplication, compaction))

  override def act() {
    Await.ready(futures, Duration.Inf)
  }

  initialize

}

object Systematic {

  private val replicateOptions = ("continuous" -> true) ~ ("filter" -> "common/to-replicate")

}
