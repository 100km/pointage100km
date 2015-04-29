package replicate.utils

import play.api.libs.json.{Json, Reads}

case class Infos(cat_names: Array[String],
                 kms_lap: Double,
                 kms_offset: Array[Double],
                 races_hours: Array[Int],
                 races_laps: Array[Int],
                 races_names: Array[String],
                 sites: Array[String],
                 sites_coordinates: Array[Infos.Coordinates],
                 start_times: Array[Long]) {

  import Infos._

  val races: Map[Int, RaceInfo] =
    races_laps.zipWithIndex.filter(_._1 != -1).map(_._2).map(id => id -> new RaceInfo(id, this)).toMap

  val checkpoints: Map[Int, CheckpointInfo] =
    (0 until sites.length).map(id => id -> new CheckpointInfo(id, this)).toMap

}

object Infos {

  case class Coordinates(latitude: Double, longitude: Double)

  implicit val coordinatesRead: Reads[Coordinates] = Json.reads[Coordinates]
  implicit val infosReads: Reads[Infos] = Json.reads[Infos]

  class RaceInfo(val raceId: Int, infos: Infos) {
    val name = infos.races_names(raceId)
    val laps = infos.races_laps(raceId)
    val startTime = infos.start_times(raceId)
    val endTime = startTime + infos.races_hours(raceId) * 24 * 3600 * 1000

    def isCheckpointTimeInRace(time: Long): Boolean = time >= startTime && time <= endTime
  }

  class CheckpointInfo(val checkpointId: Int, infos: Infos) {
    val name = infos.sites(checkpointId)
    val kms = infos.kms_offset(checkpointId)
    val coordinates = infos.sites_coordinates(checkpointId)
  }

}
