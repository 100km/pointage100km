package replicate.state

import akka.Done
import akka.agent.Agent
import play.api.libs.functional.syntax._
import play.api.libs.json._
import replicate.models.CheckpointData
import replicate.utils.{FormatUtils, Global}

import scala.concurrent.Future

object CheckpointsState {

  import Global.dispatcher

  case class Point(siteId: Int, timestamp: Long) {
    override def toString = s"Point($siteId, ${FormatUtils.formatDate(timestamp, withSeconds = true)})"
  }

  object Point {
    implicit val pointWrites: Writes[Point] = (
      (JsPath \ "site_id").write[Int] and
      (JsPath \ "time").write[Long]
    )(unlift(Point.unapply))
  }

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
