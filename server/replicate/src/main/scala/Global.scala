import akka.actor.ActorSystem
import akka.event.Logging
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration.FiniteDuration

object Global {

  val configurationFile = Config("steenwerck.cfg", "../steenwerck.cfg", "../../steenwerck.cfg")

  implicit val system = ActorSystem("Replicator", ConfigFactory.load.getConfig("replicate"))

  implicit val dispatcher = system.dispatcher

  val log = Logging(system, "Replicate")

  val config = ConfigFactory.load().getConfig("replicate")
  val backoffTimeIncrement: FiniteDuration = config.as[FiniteDuration]("changes.backoff-time-increment")
  val maximumBackoffTime: FiniteDuration = config.as[FiniteDuration]("changes.maximum-backoff-time")

}
