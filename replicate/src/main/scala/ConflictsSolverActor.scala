import akka.event.Logging
import akka.util.duration._
import net.liftweb.json._
import net.liftweb.json.Serialization.write
import net.rfc1149.canape._
import net.rfc1149.canape.helpers._

class ConflictsSolverActor(db: Database) extends PeriodicActor {

  lazy val log = Logging(context.system, this)

  protected val period = 5 seconds

  private implicit val formats = DefaultFormats

  private def getTimes(from: mapObject, name: String) = from(name).extract[List[BigInt]]

  private def times(from: mapObject): List[BigInt] = getTimes(from, "times")

  private def deletedTimes(from: mapObject): List[BigInt] = getTimes(from, "deleted_times")

  private def mergeInto(ref: mapObject, conflicting: mapObject): mapObject = {
    val deleted = deletedTimes(ref).union(deletedTimes(conflicting)).distinct.sorted
    val remaining = times(ref).union(times(conflicting)).diff(deleted).distinct.sorted
    ref + ("deleted_times" -> parse(write(deleted))) + ("times" -> parse(write(remaining)))
  }

  private def solveConflicts(id: String, revs: List[String]) = {
    try {
      val docs = getRevs(db, id, revs).execute
      (solve(db, docs) { docs => docs.tail.foldLeft(docs.head)(mergeInto(_, _)) }).execute
      log.info("solved conflicts for " + id + " (" + revs.size + " documents)")
    } catch {
	case StatusCode(status, _) =>
	  log.warning("unable to fix conflicting information for " + id + ": " + status)
    }
  }

  private def solveAllConflicts() = {
    for ((id, _, value) <- db.view("bib_input", "conflicting-checkpoints").execute.items[Nothing, List[String]])
      solveConflicts(id, value)
  }

  override def periodic() =
    try {
      solveAllConflicts()
    } catch {
      case e: Exception =>
	log.warning("unable to fix conflicting checkpoints: " + e)
    }

}
