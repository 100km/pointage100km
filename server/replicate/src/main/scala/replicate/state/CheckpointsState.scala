package replicate.state

import akka.Done
import akka.agent.Agent
import play.api.libs.json.{JsError, JsSuccess, Reads}
import replicate.utils.Global

import scala.concurrent.Future

object CheckpointsState {

  import Global.dispatcher

  case class CheckpointData(raceId: Int, contestantId: Int, siteId: Int, timestamps: Seq[Long],
      deletedTimestamps: Option[Seq[Long]], insertedTimestamps: Option[Seq[Long]]) {
    def pristine: CheckpointData =
      copy(
        timestamps         = (timestamps ++ deletedTimestamps.getOrElse(Seq())).diff(insertedTimestamps.getOrElse(Seq())).sorted,
        deletedTimestamps  = None, insertedTimestamps = None
      )
  }

  object CheckpointData {
    implicit val checkpointDataReads: Reads[CheckpointData] = Reads { js ⇒
      try {
        val raceId = (js \ "race_id").as[Int]
        val contestantId = (js \ "bib").as[Int]
        val siteId = (js \ "site_id").as[Int]
        val timestamps = (js \ "times").as[Seq[Long]]
        val deletedTimestamps = (js \ "deleted_times").asOpt[Seq[Long]]
        val insertedTimestamps = (js \ "artificial_times").asOpt[Seq[Long]]
        JsSuccess(CheckpointData(raceId, contestantId, siteId, timestamps, deletedTimestamps, insertedTimestamps))
      } catch {
        case t: Throwable ⇒ JsError(t.getMessage)
      }
    }
  }

  case class Point(siteId: Int, timestamp: Long)

  type ContestantTimes = Map[Int, Seq[Long]]
  type Race = Map[Int, ContestantTimes]

  private def sortedTimestamps(contestantTimes: ContestantTimes): Seq[Point] =
    contestantTimes.flatMap { case (siteId, ts) ⇒ ts.map(Point(siteId, _)) }.toVector.sortBy(_.timestamp)

  private def sortedTimestamps(races: Map[Int, Race], raceId: Int, contestantId: Int): Seq[Point] =
    races.get(raceId).flatMap(_.get(contestantId)).map(sortedTimestamps).getOrElse(Seq())

  private val racesAgent = Agent(Map[Int, Race]())

  def reset(): Future[Done] = racesAgent.alter(Map[Int, Race]()).map(_ ⇒ Done)

  def setTimes(checkpointData: CheckpointData): Future[Seq[Point]] = {
    val CheckpointData(raceId, contestantId, siteId, timestamps, _, _) = checkpointData
    racesAgent.alter { races ⇒
      val race = races.getOrElse(raceId, Map())
      val contestantTimes = race.getOrElse(contestantId, Map())
      val newContestantTimes = if (timestamps.isEmpty) contestantTimes - siteId else contestantTimes + (siteId → timestamps)
      val newRace = if (newContestantTimes.isEmpty) race - contestantId else race + (contestantId → newContestantTimes)
      if (newRace.isEmpty) races - raceId else races + (raceId → newRace)
    }.map(sortedTimestamps(_, raceId, contestantId))
  }

  def timesFor(raceId: Int, contestantId: Int): Future[Seq[Point]] =
    racesAgent.future().map(sortedTimestamps(_, raceId, contestantId))

  def contestants(raceId: Int): Future[Set[Int]] =
    racesAgent.future().map(_.getOrElse(raceId, Map()).keySet)
}
