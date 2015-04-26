package replicate.maintenance

import net.rfc1149.canape._
import play.api.libs.json.Json
import replicate.utils.{Global, PeriodicTaskActor}

import scala.concurrent.Future

class ReplicateRelaunch(isSlave: Boolean, local: Database, remote: Database) extends PeriodicTaskActor {

  override val period = Global.replicateRelaunchInterval

  override val immediateStart = true

  private[this] def localToRemoteReplication =
    if (!isSlave)
      local.replicateTo(remote, ReplicateRelaunch.replicateOptions)
    else
     Future.successful(null)

  private[this] def remoteToLocalReplication =
    local.replicateFrom(remote, ReplicateRelaunch.replicateOptions)

  override def futures = Seq(localToRemoteReplication, remoteToLocalReplication)

}

object ReplicateRelaunch {

  private val replicateOptions = Json.obj("continuous" -> true, "filter" -> "common/to-replicate")

}
