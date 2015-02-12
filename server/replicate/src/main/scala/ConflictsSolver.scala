import akka.event.LoggingAdapter
import net.liftweb.json._
import net.rfc1149.canape._
import net.rfc1149.canape.helpers._
import net.rfc1149.canape.util._
import scala.concurrent.Future

import Global._

trait ConflictsSolver {

  val log: LoggingAdapter

  private implicit val formats = DefaultFormats

  private def getTimes(from: mapObject, name: String) = from.get(name) match {
      case None | Some(JNull) => Nil
      case Some(l)            => l.extract[List[BigInt]]
  }

  private def times(from: mapObject): List[BigInt] = getTimes(from, "times")

  private def deletedTimes(from: mapObject): List[BigInt] = getTimes(from, "deleted_times")
  private def artificialTimes(from: mapObject): List[BigInt] = getTimes(from, "artificial_times")

  private def mergeInto(ref: mapObject, conflicting: mapObject): mapObject = {
    val deleted = deletedTimes(ref).union(deletedTimes(conflicting)).distinct.sorted
    val artificial = artificialTimes(ref).union(artificialTimes(conflicting)).distinct.sorted
    val remaining = times(ref).union(times(conflicting)).diff(deleted).distinct.sorted
    ref + ("deleted_times" -> toJValue(deleted)) + ("artificial_times" -> toJValue(artificial)) + ("times" -> toJValue(remaining))
  }

  private def solveConflicts(db: Database, id: String, revs: List[String]) =
    getRevs(db, id, revs).toFuture flatMap {
      docs =>
        val f = (solve(db, docs) {
          docs => docs.tail.foldLeft(docs.head)(mergeInto(_, _))
        }).toFuture map {
          result =>
            log.info("solved conflicts for " + id + " (" + revs.size + " documents)")
            result
        }
	f onFailure {
          case e: Exception => log.warning("unable to solve conflicts for " + id + " (" + revs.size + " documents): " + e)
        }
	f
    }

  def fixConflictingCheckpoints(db: Database) =
    db.view("common", "conflicting-checkpoints").toFuture flatMap {
      r =>
        Future.sequence(for ((id, _, value) <- r.items[Nothing, List[String]])
        yield solveConflicts(db, id, value))
    }

}
