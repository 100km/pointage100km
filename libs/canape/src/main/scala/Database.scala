package net.rfc1149.canape

import dispatch._
import dispatch.liftjson.Js._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._

object Database {

  class Status(js: JValue) {

    private implicit val formats = DefaultFormats

    val JString(db_name) = js \ "db_name"
    val JInt(doc_count) = js \ "doc_count"
    val JInt(doc_del_count) = js \ "doc_del_count"
    val JInt(update_seq) = js \ "update_seq"
    val JInt(purge_seq) = js \ "purge_seq"
    val JBool(compact_running) = js \ "compact_running"
    val JInt(disk_size) = js \ "disk_size"
    val data_size = js \ "data_size" match {
      case JInt(s) => Some(s)
      case _       => None
    }
    val instance_start_time = BigInt((js \ "instance_start_time").extract[String])
    val JInt(disk_format_version) = js \ "disk_format_version"
    val JInt(committed_update_seq) = js \ "committed_update_seq"

  }

}

case class Database(val couch: Couch, val database: String)
     extends Request(couch.couchRequest / database) {

  private[canape] val uri = couch.uri + "/" + database

  override def toString = couch.toString + "/" + database

  override def hashCode = uri.hashCode

  override def canEqual(that: Any) = that.isInstanceOf[Database]

  override def equals(that: Any): Boolean = that match {
      case other: Database if other.canEqual(this) => uri == other.uri
      case _                                       => false
  }

  private[canape] def uriFrom(other: Couch) = if (couch == other) database else uri

  def status(): Handler[Database.Status] = this ># (new Database.Status(_))

  def apply(id: String): Handler[JObject] =
    apply(id, Seq()) ~> (_.asInstanceOf[JObject])

  def apply(id: String, properties: Map[String, String]): Handler[JValue] =
    apply(id, properties.toSeq)

  def apply(id: String, properties: Seq[(String, String)]): Handler[JValue] =
    this / id <<? properties ># {js: JValue => js}

  def apply(id: String, rev: String): Handler[JObject] =
    apply(id, List("rev" -> rev)) ~> (_.asInstanceOf[JObject])

  def allDocs(params: Map[String, String] = Map()): Handler[Result[String, Map[String, JValue]]] = {
    query[String, Map[String, JValue]]("_all_docs", params)
  }

  def create(): Handler[Unit] = this.PUT >|

  def startCompaction(): Handler[Unit] =
    (this / "_compact") << ("", "application/json") >|

  def bulkDocs(docs: Seq[JValue], allOrNothing: Boolean = false): Handler[List[JObject]] = {
    val args = ("all_or_nothing" -> allOrNothing) ~ ("docs" -> docs)
    (this / "_bulk_docs").POST << (compact(render(args)), "application/json") ># { js: JValue =>
      js.children.map(_.asInstanceOf[JObject])
    }
  }

  def insert(doc: JValue, id: Option[String] = None): Handler[(String, String)] = {
    implicit val formats = DefaultFormats
    (id orElse (doc \ "_id").extractOpt[String] match {
	case Some(docId: String) => (this / docId) <<< compact(render(doc))
	case None                => this << (compact(render(doc)), "application/json")
    }) ># { js: JValue => ((js \ "id").extract[String], (js \ "rev").extract[String]) }
  }

  def insert(id: String, doc: JValue): Handler[(String, String)] =
    insert(doc, Some(id))

  def query[K: Manifest, V: Manifest](query: String,
				      params: Map[String, String] = Map(),
				      formats: Formats = DefaultFormats): Handler[Result[K, V]] =
    (this / query) <<? params ># (new Result[K, V](_, formats))

  def view[K: Manifest, V: Manifest](design: String,
				     viewName: String,
				     params: Map[String, String] = Map(),
				     formats: Formats = DefaultFormats): Handler[Result[K, V]] =
    query("_design/" + design + "/_view/" + viewName, params, formats)

  def delete(id: String, rev: String): Handler[Unit] =
    (this / id).DELETE <<? Map("rev" -> rev) >|

  def delete(): Handler[Unit] =
    this.DELETE >|

  def delete(doc: JValue): Handler[Unit] = {
    val JString(id) = doc \ "_id"
    val JString(rev) = doc \ "_rev"
    delete(id, rev)
  }

}
