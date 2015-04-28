package replicate.utils

import play.api.libs.json.{Json, Reads}

case class Infos(cat_names: Array[String],
                 kms_lap: Double,
                 kms_offset: Array[Double],
                 races_hours: Array[Int],
                 races_laps: Array[Int],
                 races_names: Array[String],
                 sites: Array[String],
                 start_times: Array[Long]) {

  import Infos._

  val races: Map[Int, RaceInfo] =
    races_laps.zipWithIndex.filter(_._1 != -1).map(_._2).map(id => id -> new RaceInfo(id, this)).toMap

}

object Infos {
  implicit val infosReads: Reads[Infos] = Json.reads[Infos]

  class RaceInfo(raceId: Int, infos: Infos) {
    val name = infos.races_names(raceId)
    val laps = infos.races_laps(raceId)
    val startTime = infos.start_times(raceId)
    val endTime = startTime + infos.races_hours(raceId) * 24 * 3600 * 1000

    def isCheckpointTimeInRace(time: Long): Boolean = time >= startTime && time <= endTime
  }

}
