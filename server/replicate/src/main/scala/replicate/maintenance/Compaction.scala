package replicate.maintenance

import net.rfc1149.canape._
import replicate.utils.PeriodicTaskActor

import scala.concurrent.duration.FiniteDuration

class Compaction(local: Database, override val period: FiniteDuration) extends PeriodicTaskActor {

  override def future = local.compact()

}
