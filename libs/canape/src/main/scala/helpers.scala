package net.rfc1149.canape

import net.liftweb.json._

package object helpers {

  import implicits._

  type Solver = Seq[mapObject] => mapObject

  def solve(db: Database,
	    documents: Seq[JObject])(solver: Solver): CouchRequest[JValue] = {
    val mergedDoc = solver(documents.map(_.toMap)) - "_conflicts"
    val JString(id) = mergedDoc("_id")
    val rev = mergedDoc("_rev")
    val deletedDocs = documents map { doc =>
      Map("_id" -> id, "_rev" -> doc \ "_rev", "_deleted" -> true)
    } filterNot { _("_rev") == rev }
    db.bulkDocs(mergedDoc +: deletedDocs, true)
  }

  def getRevs(db: Database, id: String, revs: Seq[String] = Seq()): CouchRequest[Seq[JObject]] = {
    val revsList = if (revs.isEmpty) "all" else "[" + revs.map("\"" + _ + "\"").mkString(",") + "]"
    val request = db(id, Map("open_revs" -> revsList))
    new TransformerRequest(request,
			   { js: JValue =>
			     js.childrenAs[JObject] map (_ \ "ok") map (_.asInstanceOf[JObject]) })
  }

  def getConflicting(db: Database, doc: JObject): CouchRequest[Seq[JObject]] = {
    val JString(id) = doc \ "_id"
    val revs = doc.subSeq[String]("_conflicts")
    new TransformerRequest(getRevs(db, id, revs), { docs: Seq[JObject] => doc +: docs })
  }

  def getConflictingRevs(db: Database, id: String): CouchRequest[Seq[String]] =
    new TransformerRequest(db(id, Map("conflicts" -> "true")),
			   { js: JValue =>
			     (js \ "_rev").extract[String] +: (js \ "_conflicts").extract[List[String]]
			   })

}
