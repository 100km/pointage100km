package net.rfc1149.canape

import akka.http.scaladsl.util.FastFuture
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Reads._

import scala.concurrent.{ExecutionContext, Future}

package object helpers {

  private val deleter =
    ((__ \ Symbol("_id")).json.pickBranch and (__ \ Symbol("_rev")).json.pickBranch and (__ \ Symbol("_deleted")).json.put(JsBoolean(true))).reduce

  private val unconflicter = (__ \ Symbol("_conflicts")).json.prune

  val idRevPicker = ((JsPath \ Symbol("_id")).json.pickBranch and (JsPath \ Symbol("_rev")).json.pickBranch).reduce

  def solve(db: Database, documents: Seq[JsObject])(solver: Seq[JsObject] => JsObject)(implicit context: ExecutionContext): Future[Seq[JsObject]] = {
    try {
      val mergedDoc = solver(documents)
      val rev = mergedDoc \ "_rev"
      val bulkDocs = documents map {
        case d if (d \ "_rev") == rev => mergedDoc.transform(unconflicter).get
        case d                        => d.transform(deleter).get
      }
      // Use best effort if CouchDB 2.x, and allOrNOthing if CouchDB 1.x
      db.couch.isCouchDB1.flatMap { isCouchDB1 => db.bulkDocs(bulkDocs, allOrNothing = isCouchDB1) }
    } catch { case t: Throwable => FastFuture.failed(t) }
  }

  def makeSolver[T](solver: Seq[T] => T)(implicit ev1: Reads[T], ev2: Writes[T]): (Seq[JsObject] => JsObject) =
    (docs: Seq[JsObject]) => solver(docs.map(_.as[T])).withIdRev(docs.head)

  def getRevs(db: Database, id: String, revs: Seq[String] = Seq.empty)(implicit context: ExecutionContext): Future[Seq[JsObject]] = {
    val revsList = if (revs.isEmpty) "all" else s"[${revs.map(r => s""""$r"""").mkString(",")}]"
    db(id, Map("open_revs" -> revsList)) map (_.as[Seq[JsObject]].collect {
      case j if j.keys.contains("ok") => (j \ "ok").as[JsObject]
    })
  }

  def getConflicting(db: Database, doc: JsObject)(implicit context: ExecutionContext): Future[Seq[JsObject]] = {
    val id = (doc \ "_id").as[String]
    val revs = (doc \ "_conflicts").as[Seq[String]]
    getRevs(db, id, revs) map { doc +: _ }
  }

  def getConflictingRevs(db: Database, id: String)(implicit context: ExecutionContext): Future[Seq[String]] =
    db(id, Map("conflicts" -> "true")) map { js: JsValue =>
      (js \ "_rev").as[String] +: (js \ "_conflicts").asOpt[List[String]].getOrElse(Seq.empty)
    }

  implicit class EnrichedWritable[T: Writes](obj: T) {
    def withIdRev(id: String, rev: String): JsObject = Json.toJson(obj).asInstanceOf[JsObject] ++ Json.obj("_id" -> id, "_rev" -> rev)
    def withIdRev(doc: JsObject): JsObject = Json.toJson(obj).asInstanceOf[JsObject] ++ doc.transform(idRevPicker).get
  }

}
