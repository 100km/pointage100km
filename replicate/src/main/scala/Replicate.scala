import dispatch._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._
import net.rfc1149.canape.helpers._

object Replicate {

  implicit val formats = DefaultFormats

  def getTimes(from: JValue, name: String) = (from \ name).extract[List[BigInt]]

  def times(from: JValue): List[BigInt] = getTimes(from, "times")

  def deletedTimes(from: JValue): List[BigInt] = getTimes(from, "deleted_times")

  def mergeInto(ref: JValue, conflicting: JValue): JValue = {
    val deleted = deletedTimes(ref).union(deletedTimes(conflicting)).distinct.sorted
    ref.replace("deleted_times" :: Nil, deleted).replace("times" :: Nil, times(ref).union(times(conflicting)).diff(deleted).distinct.sorted)
  }

  def mergeAllInto(docs: Seq[JValue]): JValue =
    docs.tail.foldLeft(docs.head)(mergeInto(_, _))

  def solveConflicts(db: Db, ref: JValue) = {
    val id = (ref \ "_id").extract[String]
    println("solving conflict on " + id)
    val revs = (ref \ "_conflicts").extract[List[String]]
    val conflictingDocs = Http(getRevs(db, id, revs))
    Http(solve(db, ref, conflictingDocs, mergeAllInto _))
  }

  def startReplication(couch: Couch, local: Db, remote: Db, continuous: Boolean) = {
    try {
      Http(couch.replicate(local, remote, continuous))
      Http(couch.replicate(remote, local, continuous))
    } catch {
      case StatusCode(status, _) =>
	println("unable to start replication: " + status)
    }
  }

  def main(args: Array[String]) = {
    val localCouch = Couch("admin", "admin")
    val localDb = Db(localCouch, "steenwerck100km")
    val hubCouch = Couch("tomobox.fr", 5984, "admin", "admin")
    val hubDb = Db(hubCouch, "steenwerck100km")
    while (true) {
      startReplication(localCouch, localDb, hubDb, true)
      Thread.sleep(5000)
      val conflicting = Http(new View[Nothing, JValue](localDb, "bib_input", "conflicting-checkpoints").apply()).rows
      conflicting foreach { r: Row[Nothing, JValue] => solveConflicts(localDb, r.value) }
    }
  }

}
