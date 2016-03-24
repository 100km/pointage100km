package replicate.state

import akka.agent.Agent

import scala.concurrent.Future
import replicate.utils.SortUtils._

object RankingState {

  case class Point(siteId: Int, timestamp: Long, lap: Int)

  private[replicate] case class ContestantPoints(contestantId: Int, points: Seq[Point]) {
    val maxPoint = points.lastOption

    def updatePoints(siteId: Int, timestamps: Seq[Long]): ContestantPoints = {
      val withoutSite = points.filterNot(_.siteId == siteId).map(p => (p.siteId, p.timestamp))
      var currentLap = 0
      var latestSiteId = Int.MaxValue
      val newPoints = for ((siteId, timestamp) <- (withoutSite ++ timestamps.map((siteId, _))).sortBy(_._2)) yield {
        if (siteId <= latestSiteId)
          currentLap += 1
        latestSiteId = siteId
        Point(siteId, timestamp, currentLap)
      }
      ContestantPoints(contestantId, newPoints)
    }

  }

  private[replicate] case class RaceRanking(contestants: Vector[ContestantPoints]) {

    private[this] val rankings: Map[Int, (ContestantPoints, Int)] = contestants.zipWithIndex.map { case (contestant, rm1) =>
      contestant.contestantId -> (contestant, rm1 + 1)
    }.toMap

    private[this] def updateContestant(contestant: ContestantPoints): RaceRanking = {
      val withoutContestant = contestants.filterNot(_.contestantId == contestant.contestantId)
      contestant.maxPoint match {
        case Some(_) =>
          implicit val ord = orderByRank
          RaceRanking(withoutContestant.insert(contestant))
        case None =>
          RaceRanking(withoutContestant)
      }
    }

    def updatePoints(contestantId: Int, siteId: Int, timestamps: Seq[Long]): RaceRanking = {
      val contestant = rankings.get(contestantId).fold(ContestantPoints(contestantId, Vector()))(_._1).updatePoints(siteId, timestamps)
      updateContestant(contestant)
    }

    def pointsAndRank(contestantId: Int): Option[(Seq[Point], Int)] = rankings.get(contestantId) map { case (contestant, rank) =>
      (contestant.points, rank)
    }

  }

  private val orderByRank = new Ordering[ContestantPoints] {
    override def compare(xx: ContestantPoints, yy: ContestantPoints) = {
      // No contestant can be inserted if it has no points
      val x = xx.maxPoint.get
      val y = yy.maxPoint.get
      x.lap.compare(y.lap) match {
        case 0 => x.siteId.compare(y.siteId) match {
          case 0 => x.timestamp.compare(y.timestamp) // lap and site are equal, smaller timestamp wins
          case r => -r // lap is equal, greater site win
        }
        case r => -r // greater lap wins
      }
    }
  }

  //
  // Agent implementation
  //

  import replicate.utils.Global.dispatcher

  // private[replicate] to allow testing
  private[replicate] val rankingState = Agent(Map[Int, RaceRanking]())

  /**
    * Update the timestamps at a given checkpoint for a contestant and retrieve information about their progression.
    *
    * @param contestantId the contestant bib
    * @param raceId the race id, which must be strictly positive
    * @param siteId the site id
    * @param timestamps the list of ordered timestamps at this site id
    * @return a Future containing a pair. The first element is the previous rank of the contestant, if known, and the
    *         second one is a pair with an ordered list of Point for this contestant as long as its new rank. The second
    *         element can be empty if the contestant has no longer any checkpoint information, for example because it had
    *         been entered by mistake then removed.
    */
  def updateTimestamps(contestantId: Int, raceId: Int, siteId: Int, timestamps: Seq[Long]): Future[(Option[Int], Option[(Seq[Point], Int)])] = {
    require(raceId > 0, "raceId must be strictly positive")
    for {
      previousState <- rankingState.future
      previousRank = previousState.get(raceId).flatMap(_.pointsAndRank(contestantId)).map(_._2)
      newState <- rankingState.alter { rankings =>
        val ranking = rankings.getOrElse(raceId, RaceRanking(Vector())).updatePoints(contestantId, siteId, timestamps)
        rankings + (raceId -> ranking)
      }
    } yield (previousRank, newState(raceId).pointsAndRank(contestantId))
  }

  /**
    * Retrieve the timestamps and the rank of a contestant.
    *
    * @param contestantId the contestant bib
    * @param raceId the race id
    * @return a Future containing, if known, the ordered points of this contestant and its rank in the corresponding race
    */
  def pointsAndRank(contestantId: Int, raceId: Int): Future[Option[(Seq[Point], Int)]] =
    rankingState.future.map(_.get(raceId).flatMap(_.pointsAndRank(contestantId)))

  def ranks(): Future[Map[Int, Seq[Int]]] =
    rankingState.future.map(_.mapValues(_.contestants.map(_.contestantId)))

}
