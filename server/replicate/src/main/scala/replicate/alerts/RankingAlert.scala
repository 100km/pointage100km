package replicate.alerts

import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.{Done, NotUsed}
import replicate.messaging.Message
import replicate.messaging.Message.Severity.Severity
import replicate.messaging.Message.{RaceInfo, Severity}
import replicate.state.ContestantState
import replicate.state.RankingState.RankingInfo
import replicate.utils.Types.RaceId
import replicate.utils.{Global, Glyphs}

import scala.concurrent.Future

object RankingAlert {

  /**
   * Build a message and prepend the contestant name to the body.
   */
  private[this] def alert(severity: Severity, rankingInfo: RankingInfo, message: String): Message = {
    val contestantId = rankingInfo.contestantId
    val raceId = rankingInfo.raceId
    val name = ContestantState.contestantFromId(contestantId).fold(s"Contestant $contestantId")(_.full_name_and_bib)
    val raceName = Global.infos.fold(s"Race $raceId")(_.races_names(RaceId.unwrap(raceId)))
    Message(RaceInfo, severity, title = rankingInfo.currentRank.fold(raceName)(rank ⇒ s"$raceName rank $rank"),
      body  = s"$name $message", icon = Some(Glyphs.runner))
  }

  private val rankingInfoToMessage: Flow[RankingInfo, Message, NotUsed] = Flow[RankingInfo].mapConcat { rankingInfo ⇒
    (rankingInfo.previousRank, rankingInfo.currentRank) match {
      case (Some(_), None) ⇒
        List(alert(Severity.Error, rankingInfo, "disappeared from rankings"))
      case (Some(previous), Some(current)) if previous - current >= Global.RankingAlerts.suspiciousRankJump ⇒
        List(alert(Severity.Warning, rankingInfo, s"gained ${previous - current} ranks at once (previously at rank $previous)"))
      case (Some(previous), Some(current)) if previous != current && current <= Global.RankingAlerts.topRunners ⇒
        List(alert(Severity.Info, rankingInfo, s"is in the top ${Global.RankingAlerts.topRunners} (previously at rank $previous)"))
      case (None, Some(current)) if current <= Global.RankingAlerts.topRunners ⇒
        List(alert(Severity.Info, rankingInfo, s"started directly in the top ${Global.RankingAlerts.topRunners}"))
      case _ ⇒
        Nil
    }
  }

  private val alertsSink: Sink[Message, Future[Done]] = Flow[Message].toMat(Sink.foreach(Alerts.sendAlert(_)))(Keep.right)

  def rankingAlertSink: Sink[RankingInfo, Future[Done]] = Flow[RankingInfo].via(rankingInfoToMessage).toMat(alertsSink)(Keep.right)

}
