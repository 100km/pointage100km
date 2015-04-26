package replicate.utils

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
  val heartbeatInterval: FiniteDuration = replicateConfig.as[FiniteDuration]("changes.heartbeat-interval")
  val replicateRelaunchInterval: FiniteDuration = replicateConfig.as[FiniteDuration]("replication-relaunch-interval")
  val localCompactionInterval: FiniteDuration = replicateConfig.as[FiniteDuration]("local-compaction-interval")
  val masterCompactionInterval: FiniteDuration = replicateConfig.as[FiniteDuration]("master-compaction-interval")
  val obsoleteRemoveInterval: FiniteDuration = replicateConfig.as[FiniteDuration]("obsolete-remove-interval")
  val obsoleteAge: FiniteDuration = replicateConfig.as[FiniteDuration]("obsolete-age")
  val initialReplicationTimeout: FiniteDuration = replicateConfig.as[FiniteDuration]("initial-replication-timeout")
  val raceRankingAlertInterval: FiniteDuration = replicateConfig.as[FiniteDuration]("race-ranking-alert-interval")

  var infos: Option[Infos] = None
}
