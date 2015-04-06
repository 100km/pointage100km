import Global._
import akka.event.Logging
import net.rfc1149.canape._
import play.api.libs.json.Json

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class Systematic(options: Options.Config, local: Database, remote: Option[Database]) extends PeriodicTask(30 seconds) with LoggingError {

  override val log = Logging(system, "systematic")

  private[this] def localToRemoteReplication =
    if (options.replicate && !options.isSlave)
      withError(local.replicateTo(remote.get, Systematic.replicateOptions),
        "cannot replicate from local to remote")
    else
     Future.successful(null)

  private[this] def remoteToLocalReplication =
    if (options.replicate)
      withError(local.replicateFrom(remote.get, Systematic.replicateOptions),
        "cannot replicate from remote to local")
    else
      Future.successful(null)

  private[this] var noCompactionSince: Int = 0

  private[this] def compaction = {
    noCompactionSince = (noCompactionSince + 1) % 4
    if (noCompactionSince == 0 && options.compact)
      local.compact()
    else
      Future.successful(null)
  }

  private[this] def futures =
    Future.sequence(List(localToRemoteReplication, remoteToLocalReplication, compaction))

  override def act() {
    Await.ready(futures, Duration.Inf)
  }

  initialize()

}

object Systematic {

  private val replicateOptions = Json.obj("continuous" -> true, "filter" -> "common/to-replicate")

}
