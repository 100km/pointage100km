package net.rfc1149.canape

import dispatch._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._

package object helpers {

  type ConflictSolver = Seq[JValue] => JValue

  def solve(db: Db, referenceDocument: JValue, conflictingDocuments: Seq[JValue], solver: ConflictSolver): Handler[JValue] = {
    val mergedDoc = solver(referenceDocument +: conflictingDocuments).remove(_ match {
	case JField("_conflicts", _) => true
	case _ => false
    })
    val JString(id) = referenceDocument \ "_id"
    val deletedDocs = conflictingDocuments.map { doc =>
      ("_id" -> id) ~ ("_rev" -> doc \ "_rev") ~ ("_deleted" -> true)
    }
    db.bulkDocs(mergedDoc +: deletedDocs, true)
  }

  def getRevs(db: Db, id: String, revs: Seq[String]): Handler[List[JValue]] = {
    val revsList = "[" + revs.map("\"" + _ + "\"").mkString(",") + "]"
    db(id, Map("open_revs" -> revsList)) ~> { _.children.collect {
      case JField("ok", value) => value
    } }
  }

}
