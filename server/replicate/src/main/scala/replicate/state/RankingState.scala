package replicate.state

import akka.Done
import akka.agent.Agent
import replicate.utils.RaceUtils
import replicate.utils.SortUtils._

import scala.concurrent.Future

object RankingState {

  case class Point(siteId: Int, timestamp: Long, lap: Int)

  private[replicate] case class ContestantPoints(contestantId: Int, points: Seq[Point]) {
    val maxPoint = points.lastOption

    def updatePoints(siteId: Int, timestamps: Seq[Long]): ContestantPoints = {
      val withoutSite = points.filterNot(_.siteId == siteId).map(p ⇒ (p.siteId, p.timestamp))
      val newPoints = RaceUtils.addLaps((withoutSite ++ timestamps.map((siteId, _))).sortBy(_._2))
      ContestantPoints(contestantId, newPoints)
    }

  }

  private[replicate] case class RaceRanking(contestants: Vector[ContestantPoints]) {

    private[this] val rankings: Map[Int, (ContestantPoints, Int)] = contestants.zipWithIndex.map {
      case (contestant, rm1) ⇒
        contestant.contestantId → (contestant, rm1 + 1)
    }.toMap

    private[this] def updateContestant(contestant: ContestantPoints): RaceRanking = {
      val withoutContestant = contestants.filterNot(_.contestantId == contestant.contestantId)
      contestant.maxPoint match {
        case Some(_) ⇒
          implicit val ord = orderByRank
          RaceRanking(withoutContestant.insert(contestant))
        case None ⇒
          RaceRanking(withoutContestant)
      }
    }

    def updatePoints(contestantId: Int, siteId: Int, timestamps: Seq[Long]): RaceRanking = {
      val contestant = rankings.get(contestantId).fold(ContestantPoints(contestantId, Vector()))(_._1).updatePoints(siteId, timestamps)
      updateContestant(contestant)
    }

    def pointsAndRank(contestantId: Int): Option[(Seq[Point], Int)] = rankings.get(contestantId) map {
      case (contestant, rank) ⇒
        (contestant.points, rank)
    }

  }

  private val orderByRank = new Ordering[ContestantPoints] {
    override def compare(xx: ContestantPoints, yy: ContestantPoints) = {
      // No contestant can be inserted if it has no points
      val x = xx.maxPoint.get
      val y = yy.maxPoint.get
      x.lap.compare(y.lap) match {
        case 0 ⇒ x.siteId.compare(y.siteId) match {
          case 0 ⇒ x.timestamp.compare(y.timestamp) // lap and site are equal, smaller timestamp wins
          case r ⇒ -r // lap is equal, greater site win
        }
        case r ⇒ -r // greater lap wins
      }
    }
  }

  //
  // Agent implementation
  //

  import replicate.utils.Global.dispatcher

  // private[replicate] to allow testing
  private[replicate] val rankingState = Agent(Map[Int, RaceRanking]())

  case class CheckpointData(contestantId: Int, raceId: Int, siteId: Int, timestamps: Seq[Long])

  case class RankInformation(previousRank: Option[Int], rank: Option[Int], points: Seq[Point])

  /**
   * Update the timestamps at a given checkpoint for a contestant and retrieve information about their progression.
   *
   * @param checkpointData the updated checkpoint data
   * @return the rank progression
   */
  def updateTimestamps(checkpointData: CheckpointData): Future[RankInformation] = {
    val (contestantId, raceId, siteId, timestamps) = CheckpointData.unapply(checkpointData).get
    require(raceId > 0, "raceId must be strictly positive")
    for {
      previousState ← rankingState.future
      previousRank = previousState.get(raceId).flatMap(_.pointsAndRank(contestantId)).map(_._2)
      newState ← rankingState.alter { rankings ⇒
        val ranking = rankings.getOrElse(raceId, RaceRanking(Vector())).updatePoints(contestantId, siteId, timestamps)
        rankings + (raceId → ranking)
      }
      (points, rank) = newState(raceId).pointsAndRank(contestantId).fold[(Seq[Point], Option[Int])]((Seq(), None))(par ⇒ (par._1, Some(par._2)))
    } yield RankInformation(previousRank, rank, points)
  }

  /**
   * Retrieve the timestamps and the rank of a contestant.
   *
   * @param contestantId the contestant bib
   * @param raceId the race id
   * @return a Future containing, if known, the ordered points of this contestant and its rank in the corresponding race
   */
  def pointsAndRank(contestantId: Int, raceId: Int): Future[RankInformation] =
    rankingState.future.map(_.get(raceId).flatMap(_.pointsAndRank(contestantId))).map {
      case Some((points, rank)) ⇒ RankInformation(None, Some(rank), points)
      case None                 ⇒ RankInformation(None, None, Seq())
    }

  /**
   * Ordered list of contestants for every race knwon so far.
   *
   * @return a dictionary corresponding to the contestants in every race
   */
  def ranks(): Future[Map[Int, Seq[Int]]] =
    rankingState.future.map(_.mapValues(_.contestants.map(_.contestantId)))

  /**
   * List of all the points for all the contestants in all the races.
   *
   * @return a map whose keys are a pair of contestant bib, race id, and values are the ordered list of points
   */
  def raceData(): Future[Map[(Int, Int), Seq[Point]]] =
    rankingState.future.map(_.flatMap {
      case (raceId, raceRanking) ⇒
        raceRanking.contestants.map(contestantPoints ⇒ (contestantPoints.contestantId, raceId) → contestantPoints.points)
    }.toMap)

  private[replicate] def reset(): Future[Done] =
    rankingState.alter(Map[Int, RaceRanking]()).map(_ ⇒ Done)

}
