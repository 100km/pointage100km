import Global._
import akka.event.Logging
import net.rfc1149.canape._
import play.api.libs.json.Json

import scala.concurrent.Future

class ReplicateRelaunch(isSlave: Boolean, local: Database, remote: Database) extends PeriodicTask(replicateRelaunchInterval) with LoggingError {

  override val log = Logging(system, "replicate-relaunch")

  private[this] def localToRemoteReplication =
    if (!isSlave)
      withError(local.replicateTo(remote, ReplicateRelaunch.replicateOptions),
        "cannot replicate from local to remote")
    else
     Future.successful(null)

  private[this] def remoteToLocalReplication =
    withError(local.replicateFrom(remote, ReplicateRelaunch.replicateOptions),
      "cannot replicate from remote to local")

  override def futures = Seq(localToRemoteReplication, remoteToLocalReplication)

  initialize()

}

object ReplicateRelaunch {

  private val replicateOptions = Json.obj("continuous" -> true, "filter" -> "common/to-replicate")

}
