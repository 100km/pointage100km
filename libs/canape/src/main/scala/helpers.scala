package net.rfc1149.canape

import dispatch._
import dispatch.liftjson.Js._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._

package object helpers {

  type Solver = Seq[JObject] => JObject

  def solve(db: Database,
	    documents: Seq[JObject])(solver: Solver): Handler[List[JObject]] = {
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

  def getRevs(db: Database, id: String, revs: Seq[String] = Seq()): Handler[List[JObject]] = {
    val revsList = if (revs.isEmpty) "all" else "[" + revs.map("\"" + _ + "\"").mkString(",") + "]"
    db(id, Map("open_revs" -> revsList)) ~> { _.children.collect {
      case JObject(JField("ok", value: JObject) :: _) => value
    } }
  }

  def getConflicting(db: Database, doc: JObject): Handler[List[JObject]] = {
    implicit val formats = DefaultFormats
    val JString(id) = doc \ "_id"
    val revs = (doc \ "_conflicts").extract[List[String]]
    getRevs(db, id, revs) ~> (doc :: _)
  }

  def getConflictingRevs(db: Database, id: String): Handler[List[String]] = {
    implicit val formats = DefaultFormats
    db(id, Map("conflicts" -> "true")) ~> { doc =>
      (doc \ "_rev").extract[String] :: (doc \ "_conflicts").extract[List[String]]
    }
  }

}
