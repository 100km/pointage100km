import akka.actor.ActorSystem
import akka.event.Logging
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration.FiniteDuration

object Global {
  private val config: Config = steenwerck.config.withFallback(ConfigFactory.load())

  private val replicateConfig = config.getConfig("replicate")
  implicit val system = ActorSystem("replicator", replicateConfig)
  implicit val dispatcher = system.dispatcher
  val log = Logging(system, "replicate")

  val backoffTimeIncrement: FiniteDuration = replicateConfig.as[FiniteDuration]("changes.backoff-time-increment")
  val maximumBackoffTime: FiniteDuration = replicateConfig.as[FiniteDuration]("changes.maximum-backoff-time")
}
