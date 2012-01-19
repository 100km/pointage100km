package net.rfc1149.canape

import dispatch._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._

package object helpers {

  type ConflictSolver = Seq[JValue] => JValue

  def solve(db: Db, documents: Seq[JValue], solver: ConflictSolver): Handler[JValue] = {
    val mergedDoc = solver(documents).remove(_ match {
	case JField("_conflicts", _) => true
	case _ => false
    })
    val JString(id) = mergedDoc \ "_id"
    val JString(rev) = mergedDoc \ "_rev"
    val deletedDocs = documents map { doc =>
      ("_id" -> id) ~ ("_rev" -> doc \ "_rev") ~ ("_deleted" -> true)
    } filterNot { _ \ "_rev" == rev }
    db.bulkDocs(mergedDoc +: deletedDocs, true)
  }

  def getRevs(db: Db, id: String, revs: Seq[String] = Seq()): Handler[List[JValue]] = {
    val revsList = if (revs.isEmpty) "all" else "[" + revs.map("\"" + _ + "\"").mkString(",") + "]"
    db(id, Map("open_revs" -> revsList)) ~> { _.children.collect {
      case JObject(JField("ok", value) :: _) => value
    } }
  }

}
