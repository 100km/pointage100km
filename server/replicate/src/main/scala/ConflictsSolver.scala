import Global._
import akka.event.LoggingAdapter
import net.rfc1149.canape._
import net.rfc1149.canape.helpers._
import play.api.libs.json.Reads._
import play.api.libs.json._

import scala.concurrent.Future

trait ConflictsSolver {

  val log: LoggingAdapter

  private implicit class TimesAccessor(from: JsObject) {
    def getTimes(name: String): List[BigDecimal] = from.validate((__ \ name).json.pick[JsArray]) match {
      case JsSuccess(l, _) => l.as[List[BigDecimal]]
      case _: JsError      => Nil
    }
    def times = getTimes("times")
    def deletedTimes = getTimes("deleted_times")
    def artificialTimes = getTimes("artificial_times")
    def setTimes(name: String, times: List[BigDecimal]): JsObject =
      from - name ++ Json.obj(name -> times)
  }

  private def mergeInto(ref: JsObject, conflicting: JsObject): JsObject = {
    val deleted = ref.deletedTimes.union(conflicting.deletedTimes).distinct.sorted
    val artificial = ref.artificialTimes.union(conflicting.artificialTimes).distinct.sorted
    val remaining = ref.times.union(conflicting.times).diff(deleted).distinct.sorted
    ref.setTimes("deleted_times", deleted).setTimes("artificial_times", artificial).setTimes("times", remaining)
  }

  private def solveConflicts(db: Database, id: String, revs: List[String]): Future[Seq[JsObject]] =
    getRevs(db, id, revs) flatMap {
      docs =>
        val f = solve(db, docs) {
          docs => docs.tail.foldLeft(docs.head)(mergeInto)
        } map {
          result =>
            log.info("solved conflicts for " + id + " (" + revs.size + " documents)")
            result
        }
        f onFailure {
          case e: Exception => log.warning("unable to solve conflicts for " + id + " (" + revs.size + " documents): " + e)
        }
        f
    }

  def fixConflictingCheckpoints(db: Database): Future[Iterable[Seq[JsObject]]] =
    db.view("common", "conflicting-checkpoints") flatMap {
      r =>
        Future.sequence(for ((id, _, value) <- r.items[JsValue, List[String]])
          yield solveConflicts(db, id, value))
    }

}
