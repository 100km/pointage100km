import akka.event.Logging
import akka.util.duration._
import net.rfc1149.canape._

class ReplicationActor(couch: Couch, local: Database, remote: Database) extends PeriodicActor {

  private val log = Logging(context.system, this)

  val period = 5 seconds

  override def periodic() =
    try {
      couch.replicate(local, remote, true).execute
      couch.replicate(remote, local, true).execute
    } catch {
      case e: Exception =>
	log.warning("unable to replicate: " + e)
    }

}
