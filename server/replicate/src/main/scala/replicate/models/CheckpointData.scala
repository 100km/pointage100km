package replicate.models

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class CheckpointData(raceId: Int, contestantId: Int, siteId: Int, timestamps: List[Long],
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
      insertedTimestamps = Nil
    )

  /**
   * Merge the times of another checkpoint into this one. The other fields will not be checked
   * or changed.
   *
   * @param other the other checkpoint
   * @return the merged checkpoint
   */
  def merge(other: CheckpointData) = {
    val deleted = (deletedTimestamps ++ other.deletedTimestamps).distinct.sorted
    val inserted = (insertedTimestamps ++ other.insertedTimestamps).distinct.sorted
    val times = (timestamps ++ other.timestamps ++ inserted).diff(deleted).distinct.sorted
    copy(timestamps = times, deletedTimestamps = deleted, insertedTimestamps = inserted)
  }
}

object CheckpointData {

  implicit val checkpointDataReads: Reads[CheckpointData] = Reads { js ⇒
    try {
      val raceId = (js \ "race_id").as[Int]
      val contestantId = (js \ "bib").as[Int]
      val siteId = (js \ "site_id").as[Int]
      val timestamps = (js \ "times").as[List[Long]]
      val deletedTimestamps = (js \ "deleted_times").asOpt[List[Long]].getOrElse(Nil)
      val insertedTimestamps = (js \ "artificial_times").asOpt[List[Long]].getOrElse(Nil)
      JsSuccess(CheckpointData(raceId, contestantId, siteId, timestamps, deletedTimestamps, insertedTimestamps))
    } catch {
      case t: Throwable ⇒ JsError(t.getMessage)
    }
  }

  implicit val checkpointDataWrites: Writes[CheckpointData] = (
    (JsPath \ "race_id").write[Int] and
    (JsPath \ "bib").write[Int] and
    (JsPath \ "site_id").write[Int] and
    (JsPath \ "times").write[List[Long]] and
    (JsPath \ "deleted_times").write[List[Long]] and
    (JsPath \ "artificial_times").write[List[Long]]
  )(unlift(CheckpointData.unapply))
}
