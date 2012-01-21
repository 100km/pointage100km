import akka.util.duration._
import dispatch._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._
import net.rfc1149.canape.helpers._

class ConflictsSolverActor(db: Database) extends DispatchActor with PeriodicActor {

  protected val period = 5 seconds

  private implicit val formats = DefaultFormats

  private def getTimes(from: JObject, name: String) = (from \ name).extract[List[BigInt]]

  private def times(from: JObject): List[BigInt] = getTimes(from, "times")

  private def deletedTimes(from: JObject): List[BigInt] = getTimes(from, "deleted_times")

  private def mergeInto(ref: JObject, conflicting: JObject): JObject = {
    val deleted = deletedTimes(ref).union(deletedTimes(conflicting)).distinct.sorted
    ref.replace("deleted_times" :: Nil, deleted).replace("times" :: Nil, times(ref).union(times(conflicting)).diff(deleted).distinct.sorted).asInstanceOf[JObject]
  }

  private def solveConflicts(id: String, revs: List[String]) = {
    try {
      val docs = http(getRevs(db, id, revs))
      http(solve(db, docs) { docs => docs.tail.foldLeft(docs.head)(mergeInto(_, _)) })
      log.info("solved conflicts for " + id + " (" + revs.size + " documents)")
    } catch {
	case StatusCode(status, _) =>
	  log.warning("unable to fix conflicting information for " + id + ": " + status)
    }
  }

  private def solveAllConflicts() = {
    for (row <- http(db.view[Nothing, List[String]]("bib_input", "conflicting-checkpoints")).rows)
      solveConflicts(row.id, row.value)
  }

  override def periodic() =
    try {
      solveAllConflicts()
    } catch {
      case e: Exception =>
	log.warning("unable to fix conflicting checkpoints: " + e)
    }

}
