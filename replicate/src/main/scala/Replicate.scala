import dispatch._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._
import net.rfc1149.canape.helpers._

object Replicate {

  val config = Config("steenwerck.cfg")

  implicit val formats = DefaultFormats

  def getTimes(from: JValue, name: String) = (from \ name).extract[List[BigInt]]

  def times(from: JValue): List[BigInt] = getTimes(from, "times")

  def deletedTimes(from: JValue): List[BigInt] = getTimes(from, "deleted_times")

  def mergeInto(ref: JValue, conflicting: JValue): JValue = {
    val deleted = deletedTimes(ref).union(deletedTimes(conflicting)).distinct.sorted
    ref.replace("deleted_times" :: Nil, deleted).replace("times" :: Nil, times(ref).union(times(conflicting)).diff(deleted).distinct.sorted)
  }

  def solveConflicts(db: Database, id: String, revs: List[String]) = {
    println("solving conflicts for " + id + " (" + revs.size + " documents)")
    val docs = Http(getRevs(db, id, revs))
    Http(solve(db, docs) { docs => docs.tail.foldLeft(docs.head)(mergeInto(_, _)) })
  }

  def startReplication(couch: Couch, local: Database, remote: Database, continuous: Boolean) = {
    try {
      Http(couch.replicate(local, remote, continuous))
      Http(couch.replicate(remote, local, continuous))
    } catch {
      case StatusCode(status, _) =>
	println("unable to start replication: " + status)
    }
  }

  def fixIncompleteCheckpoints(db: Database) =
    for (doc <- Http(db.view[Nothing, JValue]("bib_input", "incomplete-checkpoints")).values) {
      try {
	val JInt(bib) = doc \ "bib"
	val JInt(race) = Http(db("infos-" + bib)) \ "course"
	if (race != 0) {
	  println("Fixing incomplete race " + race + " for bib " + bib)
	  Http(db.insert(doc.replace("race_id" :: Nil, race)))
	}
      } catch {
	  case x: Exception =>
	    println("Unable to fix contestant: " + x)
      }
    }

  def main(args: Array[String]) = {
    val localCouch = Couch("admin", "admin")
    val localDatabase = Database(localCouch, "steenwerck100km")
    val hubCouch = Couch(config.read[String]("master.host"),
			 config.read[Int]("master.port"),
			 config.read[String]("master.user"),
			 config.read[String]("master.password"))
    val hubDatabase = Database(hubCouch, config.read[String]("master.dbname"))

    try {
      Http(localDatabase.create)
    } catch {
	case StatusCode(status, _) =>
	  println("cannot create database: " + status)
    }

    while (true) {
      startReplication(localCouch, localDatabase, hubDatabase, true)
      Thread.sleep(5000)
      val conflicting = Http(localDatabase.view[Nothing, List[String]]("bib_input", "conflicting-checkpoints")).rows
      for (row <- conflicting) {
	solveConflicts(localDatabase, row.id, row.value)
      }
      fixIncompleteCheckpoints(localDatabase)
    }
  }

}
