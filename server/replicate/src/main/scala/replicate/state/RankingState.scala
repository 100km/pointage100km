package replicate.state

import akka.agent.Agent
import replicate.scrutineer.Analyzer.{ContestantAnalysis, KeepPoint}
import replicate.utils.Global
import replicate.utils.SortUtils._

import scala.concurrent.Future

object RankingState {

  import Global.dispatcher

  case class Rank(contestantId: Int, bestPoint: KeepPoint)

  type Ranks = Vector[Rank]

  private implicit val rankOrdering = new Ordering[Rank] {
    override def compare(x: Rank, y: Rank) = (x.bestPoint.lap, y.bestPoint.lap) match {
      // Bigger lap is best
      case (xl, yl) if xl < yl ⇒ 1
      case (xl, yl) if xl > yl ⇒ -1
      case _ ⇒
        // Same lap, bigger siteId is best
        (x.bestPoint.point.siteId, y.bestPoint.point.siteId) match {
          case (xs, ys) if xs < ys ⇒ 1
          case (xs, ys) if xs > ys ⇒ -1
          case _ ⇒
            // Same lap, same siteId, smaller timestamp is best
            x.bestPoint.point.timestamp.compare(y.bestPoint.point.timestamp)
        }
    }
  }

  private def removeContestant(contestantId: Int, ranks: Ranks): Ranks =
    ranks.filterNot(_.contestantId == contestantId)

  private def addContestant(rank: Rank, ranks: Ranks): Ranks =
    removeContestant(rank.contestantId, ranks).insert(rank)

  private val rankings = Agent(Map[Int, Ranks]())

  def rankingsFor(raceId: Int): Future[Ranks] = rankings.future().map(_.getOrElse(raceId, Vector()))

  private def enterBestPoint(raceId: Int, contestantId: Int, point: KeepPoint): Future[Map[Int, Ranks]] = rankings.alter { ranks ⇒
    ranks + (raceId → addContestant(Rank(contestantId, point), ranks.getOrElse(raceId, Vector())))
  }

  private def removePoints(raceId: Int, contestantId: Int): Future[Map[Int, Ranks]] = rankings.alter { ranks ⇒
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

}
