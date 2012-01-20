import akka.util.duration._
import dispatch._
import net.rfc1149.canape._

class ReplicationActor(couch: Couch, local: Database, remote: Database) extends DispatchActor with PeriodicActor {

  val period = 5 seconds

  override def periodic() =
    try {
      http(couch.replicate(local, remote, true))
      http(couch.replicate(remote, local, true))
    } catch {
      case StatusCode(status, _) =>
	log.warning("unable to replicate: " + status)
    }

}
