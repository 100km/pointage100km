package replicate.state

import replicate.scrutineer.Analyzer.{ContestantAnalysis, KeepPoint}
import replicate.utils.SortUtils._
import replicate.utils.Types._
import replicate.utils.{Agent, Global}

import scala.concurrent.Future
import scalaz.@@

object RankingState {

  import Global.dispatcher

  case class Rank(contestantId: Int @@ ContestantId, bestPoint: KeepPoint)

  type Ranks = Vector[Rank]

  private implicit val rankOrdering = new Ordering[Rank] {
    override def compare(x: Rank, y: Rank) = (x.bestPoint.lap, y.bestPoint.lap) match {
      // Bigger lap is best
      case (xl, yl) if Lap.unwrap(xl) < Lap.unwrap(yl) ⇒ 1
      case (xl, yl) if Lap.unwrap(xl) > Lap.unwrap(yl) ⇒ -1
      case _ ⇒
        // Same lap, bigger siteId is best
        (x.bestPoint.point.siteId, y.bestPoint.point.siteId) match {
          case (xs, ys) if SiteId.unwrap(xs) < SiteId.unwrap(ys) ⇒ 1
          case (xs, ys) if SiteId.unwrap(xs) > SiteId.unwrap(ys) ⇒ -1
          case _ ⇒
            // Same lap, same siteId, smaller timestamp is best
            x.bestPoint.point.timestamp.compare(y.bestPoint.point.timestamp)
        }
    }
  }

  private def removeContestant(contestantId: Int @@ ContestantId, ranks: Ranks): Ranks =
    ranks.filterNot(_.contestantId == contestantId)

  private def addContestant(rank: Rank, ranks: Ranks): Ranks =
    removeContestant(rank.contestantId, ranks).insert(rank)

  private val rankings = Agent(Map[Int @@ RaceId, Ranks]())

  def rankingsFor(raceId: Int @@ RaceId): Future[Ranks] = rankings.future().map(_.getOrElse(raceId, Vector()))

  private def enterBestPoint(raceId: Int @@ RaceId, contestantId: Int @@ ContestantId, point: KeepPoint): Future[Map[Int @@ RaceId, Ranks]] =
    rankings.alter { ranks ⇒
      ranks + (raceId → addContestant(Rank(contestantId, point), ranks.getOrElse(raceId, Vector())))
    }

  private def removePoints(raceId: Int @@ RaceId, contestantId: Int @@ ContestantId): Future[Map[Int @@ RaceId, Ranks]] = rankings.alter { ranks ⇒
    ranks + (raceId → removeContestant(contestantId, ranks.getOrElse(raceId, Vector())))
  }

  def enterAnalysis(analysis: ContestantAnalysis): Future[Ranks] = {
    val raceId = analysis.raceId
    val contestantId = analysis.contestantId
    val allRanks = analysis.bestPoint match {
      case Some(bestPoint) ⇒ enterBestPoint(raceId, contestantId, bestPoint)
      case None            ⇒ removePoints(raceId, contestantId)
    }
    allRanks.map(_.getOrElse(raceId, Vector()))
  }

  private def rankFor(ranks: Ranks, contestantId: Int @@ ContestantId): Option[Int] =
    ranks.indexWhere(_.contestantId == contestantId) match {
      case -1 ⇒ None
      case n  ⇒ Some(n + 1)
    }

  def enterContestant(analysis: ContestantAnalysis): Future[RankingInfo] = {
    val previousRank = rankingsFor(analysis.raceId).map(rankFor(_, analysis.contestantId))
    val currentRank = enterAnalysis(analysis).map(rankFor(_, analysis.contestantId))
    for (p ← previousRank; c ← currentRank) yield RankingInfo(analysis, p, c)
  }

  case class RankingInfo(analysis: ContestantAnalysis, previousRank: Option[Int], currentRank: Option[Int]) {
    def contestantId: Int @@ ContestantId = analysis.contestantId
    def raceId: Int @@ RaceId = analysis.raceId
  }

}
