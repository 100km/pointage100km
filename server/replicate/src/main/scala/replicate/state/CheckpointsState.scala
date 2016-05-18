package replicate.state

import akka.Done
import akka.agent.Agent
import play.api.libs.functional.syntax._
import play.api.libs.json._
import replicate.models.CheckpointData
import replicate.utils.Types._
import replicate.utils.{FormatUtils, Global}

import scala.concurrent.Future
import scalaz.@@

object CheckpointsState {

  import Global.dispatcher

  case class Point(siteId: Int @@ SiteId, timestamp: Long) {
    override def toString = s"Point($siteId, ${FormatUtils.formatDate(timestamp, withSeconds = true)})"
  }

  object Point {
    implicit val pointWrites: Writes[Point] = (
      (JsPath \ "site_id").write[Int @@ SiteId] and
      (JsPath \ "time").write[Long]
    )(unlift(Point.unapply))
  }

  type Race = Map[Int @@ ContestantId, Seq[CheckpointData]]

  def toPoints(siteId: Int @@ SiteId, timestamps: Seq[Long]): Seq[Point] = timestamps.map(Point(siteId, _))

  def sortedTimestamps(contestantTimes: Seq[CheckpointData]): Seq[Point] =
    contestantTimes.flatMap {
      case CheckpointData(_, _, siteId, ts, _, _) ⇒ ts.map(Point(siteId, _))
    }.toVector.sortBy(_.timestamp)

  private def sortedTimestamps(races: Map[Int @@ RaceId, Race], raceId: Int @@ RaceId, contestantId: Int @@ ContestantId): Seq[Point] =
    races.get(raceId).flatMap(_.get(contestantId)).map(sortedTimestamps).getOrElse(Seq())

  private val racesAgent = Agent(Map[Int @@ RaceId, Race]())

  def reset(): Future[Done] = racesAgent.alter(Map[Int @@ RaceId, Race]()).map(_ ⇒ Done)

  def setTimes(checkpointData: CheckpointData): Future[Seq[CheckpointData]] = {
    val CheckpointData(raceId, contestantId, siteId, _, _, _) = checkpointData
    racesAgent.alter { races ⇒
      val race = races.getOrElse(raceId, Map())
      val contestantTimes = race.getOrElse(contestantId, Seq()).filterNot(_.siteId == siteId) :+ checkpointData
      val newRace = race + (contestantId → contestantTimes)
      races + (raceId → newRace)
    }.map(_(raceId)(contestantId))
  }

  def checkpointDataFor(raceId: Int @@ RaceId, contestantId: Int @@ ContestantId): Future[Seq[CheckpointData]] =
    racesAgent.future().map(_.getOrElse(raceId, Map()).getOrElse(contestantId, Seq()))

  def timesFor(raceId: Int @@ RaceId, contestantId: Int @@ ContestantId): Future[Seq[Point]] =
    racesAgent.future().map(sortedTimestamps(_, raceId, contestantId))

  def contestants(raceId: Int @@ RaceId): Future[Set[Int @@ ContestantId]] =
    racesAgent.future().map(_.getOrElse(raceId, Map()).mapValues(sortedTimestamps).filterNot(_._2.isEmpty).keySet)
}
