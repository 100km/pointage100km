package replicate.models

import play.api.libs.json._
import play.api.libs.functional.syntax._
import replicate.utils.Types._

import scalaz.@@

case class CheckpointData(raceId: Int @@ RaceId, contestantId: Int @@ ContestantId, siteId: Int @@ SiteId, timestamps: List[Long],
    deletedTimestamps: List[Long], insertedTimestamps: List[Long]) {

  /**
   * Create a pristine checkpoint, with only the original times. Deleted times are reinstated,
   * and inserted times are removed.
   *
   * @return a pristine checkpoint
   */
  def pristine: CheckpointData =
    copy(
      timestamps         = (timestamps ++ deletedTimestamps).diff(insertedTimestamps).distinct.sorted,
      deletedTimestamps  = Nil,
      insertedTimestamps = Nil)

  /**
   * Merge the times of another checkpoint into this one. The other fields will not be checked
   * or changed.
   *
   * @param other the other checkpoint
   * @return the merged checkpoint
   */
  def merge(other: CheckpointData): CheckpointData = {
    val deleted = (deletedTimestamps ++ other.deletedTimestamps).distinct.sorted
    val inserted = (insertedTimestamps ++ other.insertedTimestamps).distinct.sorted
    val times = (timestamps ++ other.timestamps ++ inserted).diff(deleted).distinct.sorted
    copy(timestamps         = times, deletedTimestamps = deleted, insertedTimestamps = inserted)
  }
}

object CheckpointData {

  // We are lenient about the presence of `deleted_times` and `artificial_times` as some older
  // administration tools may not insert them initially.
  implicit val checkpointDataReads: Reads[CheckpointData] = Reads { js ⇒
    try {
      val raceId = RaceId((js \ "race_id").as[Int])
      val contestantId = ContestantId((js \ "bib").as[Int])
      val siteId = SiteId((js \ "site_id").as[Int])
      val timestamps = (js \ "times").as[List[Long]]
      val deletedTimestamps = (js \ "deleted_times").asOpt[List[Long]].getOrElse(Nil)
      val insertedTimestamps = (js \ "artificial_times").asOpt[List[Long]].getOrElse(Nil)
      JsSuccess(CheckpointData(raceId, contestantId, siteId, timestamps, deletedTimestamps, insertedTimestamps))
    } catch {
      case t: Throwable ⇒ JsError(t.getMessage)
    }
  }

  implicit val checkpointDataWrites: Writes[CheckpointData] = (
    (JsPath \ "race_id").write[Int @@ RaceId] and
    (JsPath \ "bib").write[Int @@ ContestantId] and
    (JsPath \ "site_id").write[Int @@ SiteId] and
    (JsPath \ "times").write[List[Long]] and
    (JsPath \ "deleted_times").write[List[Long]] and
    (JsPath \ "artificial_times").write[List[Long]])(unlift(CheckpointData.unapply))
}
