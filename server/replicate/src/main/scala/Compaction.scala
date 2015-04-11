import Global._
import akka.event.Logging
import net.rfc1149.canape._

class Compaction(local: Database) extends PeriodicTask(replicateRelaunchInterval) with LoggingError {

  override val log = Logging(system, "compaction")

  override def futures = Seq(local.compact())

  initialize()

}
