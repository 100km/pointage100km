package replicate.utils

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import play.api.libs.json.{Json, Reads}
import replicate.utils.Types._

import scalaz.@@
import scalaz.Scalaz._

case class Infos(
    cat_names: Array[String],
    kms_lap: Double,
    kms_offset: Array[Double],
    races_hours: Array[Int],
    races_laps: Array[Int],
    races_names: Array[String],
    sites: Array[String],
    sites_coordinates: Array[Infos.Coordinates],
    start_times: Array[Long],
    timezone: String) {

  import Infos._

  val races: Map[Int @@ RaceId, RaceInfo] =
    races_laps.zipWithIndex.filter(_._1 != -1).map(_._2).map(RaceId[Int]).map(id => id -> new RaceInfo(id, this)).toMap

  val checkpoints: Map[Int @@ SiteId, CheckpointInfo] =
    sites.indices.map(SiteId[Int]).map(id => id -> new CheckpointInfo(id, this)).toMap

  /**
   * Mapping of distances in kilometers from (siteId, lap)
   */
  val distances: Map[(Int @@ SiteId, Int @@ Lap), Double] = {
    var d: Map[(Int @@ SiteId, Int @@ Lap), Double] = Map()
    for (lap <- (1 |-> races_laps.max).map(Lap[Int]); siteId <- sites.indices.map(SiteId[Int])) {
      d += (siteId, lap) -> distance(siteId, lap)
    }
    d
  }

  def distance(siteId: Int @@ SiteId, lap: Int @@ Lap): Double = kms_lap * (Lap.unwrap(lap) - 1) + kms_offset(SiteId.unwrap(siteId))

}

object Infos {

  case class Coordinates(latitude: Double, longitude: Double) {
    lazy val url = Uri("https://maps.google.com/maps").withQuery(Query("q" -> s"loc:$latitude,$longitude"))
  }

  implicit val coordinatesRead: Reads[Coordinates] = Json.reads[Coordinates]
  implicit val infosReads: Reads[Infos] = Json.reads[Infos]

  class RaceInfo(val raceId: Int @@ RaceId, infos: Infos) {
    val name = infos.races_names(RaceId.unwrap(raceId))
    val laps = infos.races_laps(RaceId.unwrap(raceId))
    val startTime = infos.start_times(RaceId.unwrap(raceId))
    val endTime = startTime + infos.races_hours(RaceId.unwrap(raceId)) * 3600 * 1000

    def isCheckpointTimeInRace(time: Long): Boolean = time >= startTime && time <= endTime
  }

  class CheckpointInfo(val checkpointId: Int @@ SiteId, infos: Infos) {
    val name = infos.sites(SiteId.unwrap(checkpointId))
    val kms = infos.kms_offset(SiteId.unwrap(checkpointId))
    val coordinates = infos.sites_coordinates(SiteId.unwrap(checkpointId))
  }

}
