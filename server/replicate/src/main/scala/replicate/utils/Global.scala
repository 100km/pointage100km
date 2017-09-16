package replicate.utils

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._
import replicate.scrutineer.Analyzer.ContestantAnalysis

import scala.concurrent.duration.FiniteDuration

object Global {
  private val config: Config = steenwerck.steenwerckRootConfig.withFallback(ConfigFactory.load())

  private[replicate] val replicateConfig = config.getConfig("replicate").withFallback(config)
  implicit val system = ActorSystem("replicator", replicateConfig)
  implicit val dispatcher = system.dispatcher
  implicit val flowMaterializer = ActorMaterializer()
  val log = Logging(system, "replicate")

  val replicateRelaunchInterval: FiniteDuration = replicateConfig.as[FiniteDuration]("replication-relaunch-interval")
  val localCompactionInterval: FiniteDuration = replicateConfig.as[FiniteDuration]("local-compaction-interval")
  val masterCompactionInterval: FiniteDuration = replicateConfig.as[FiniteDuration]("master-compaction-interval")
  val obsoleteRemoveInterval: FiniteDuration = replicateConfig.as[FiniteDuration]("obsolete-remove-interval")
  val obsoleteAge: FiniteDuration = replicateConfig.as[FiniteDuration]("obsolete-age")
  val initialReplicationTimeout: FiniteDuration = replicateConfig.as[FiniteDuration]("initial-replication-timeout")
  val pingTimeout: FiniteDuration = replicateConfig.as[FiniteDuration]("ping-timeout")
  val stalkersObsoleteDuration: FiniteDuration = replicateConfig.as[FiniteDuration]("stalkers-obsolete-duration")

  object RankingAlerts {
    private val rankingAlertsConfig = replicateConfig.as[Config]("ranking-alerts")
    val topRunners: Int = rankingAlertsConfig.as[Int]("top-runners")
    val suspiciousRankJump: Int = rankingAlertsConfig.as[Int]("suspicious-rank-jump")
  }

  object CheckpointAlerts {
    private val checkpointAlertsConfig = replicateConfig.as[Config]("checkpoint-alerts")
    val noticeDelay: FiniteDuration = checkpointAlertsConfig.as[FiniteDuration]("notice-delay")
    val warningDelay: FiniteDuration = checkpointAlertsConfig.as[FiniteDuration]("warning-delay")
    val criticalDelay: FiniteDuration = checkpointAlertsConfig.as[FiniteDuration]("critical-delay")
    assert(warningDelay > noticeDelay)
    assert(criticalDelay > warningDelay)
  }

  object BroadcastAlerts {
    private val broadcastAlertsConfig = replicateConfig.as[Config]("broadcast-alerts")
    val checkInterval: FiniteDuration = broadcastAlertsConfig.as[FiniteDuration]("check-interval")
  }

  object TextMessages {
    private val textMessagesConfig = replicateConfig.as[Config]("text-messages")
    private val stabilizationDelay = textMessagesConfig.as[FiniteDuration]("stabilization-delay")
    private val maxQueueSize = textMessagesConfig.as[Int]("max-queue-size")
    val maxAcceptableDelay = textMessagesConfig.as[FiniteDuration]("max-acceptable-delay")

    val ifAnalysisUnchanged =
      Flow[ContestantAnalysis].via(StreamUtils.ifUnchangedAfter(_.contestantId, stabilizationDelay, maxQueueSize)).named("ifAnalysisUnchanged")

    object TopUp {
      private val topUpConfig = textMessagesConfig.as[Config]("top-up")
      val noticeAmount: Double = topUpConfig.as[Double]("notice-amount")
      val warningAmount: Double = topUpConfig.as[Double]("warning-amount")
      val criticalAmount: Double = topUpConfig.as[Double]("critical-amount")
      assert(warningAmount < noticeAmount)
      assert(criticalAmount < warningAmount)
    }
  }

  var infos: Option[Infos] = None
  var configuration: Option[Configuration] = None
}
