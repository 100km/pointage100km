package net.rfc1149.canape

import dispatch._
import dispatch.liftjson.Js._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._

case class Couch(val host: String = "localhost", val port: Int = 5984, val auth: Option[(String, String)] = None) {

  val uri = "http://" + auth.map(x => x._1 + ":" + x._2 + "@").getOrElse("") + host + ":" + port

  val couchRequest = {
    val base = :/(host, port)
    auth match {
      case Some((login, password)) => base.as_!(login, password)
      case None                    => base
    }
  }

  def replicate(source: Db, target: Db, continuous: Boolean) = {
    val params = ("source" -> source.uriFrom(this)) ~
                 ("target" -> target.uriFrom(this)) ~
                 ("continuous" -> continuous)
    couchRequest / "_replicate" << (compact(render(params)), "application/json") >|
  }

  def status = couchRequest ># (new CouchStatus(_))

}

object Couch {

  def apply(host: String, port: Int, login: String, password: String): Couch = Couch(host, port, Some((login, password)))

  def apply(login: String, password: String): Couch = Couch(auth = Some((login, password)))

  def apply(host: String, login: String, password: String): Couch = Couch(host, auth = Some((login, password)))

}

class CouchStatus(js: JValue) {

  private implicit val formats = DefaultFormats

  val JString(couchdb) = js \ "couchdb"
  val JString(version) = js \ "version"
  val vendorVersion = (js \ "vendor" \ "version").extractOpt[String]
  val vendorName = (js \ "vendor" \ "name").extractOpt[String]

}

class DbStatus(js: JValue) {

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

case class Db(val couch: Couch, val database: String) extends Request(couch.couchRequest / database) {

  implicit private val formats = DefaultFormats

  val uri = couch.uri + "/" + database

  private[canape] def uriFrom(other: Couch) = if (couch == other) database else uri

  def status = this ># (new DbStatus(_))

  def apply(id: String) = this / id

  def apply(id: String, rev: String) = this / id <<? List("rev" -> rev)

  lazy val allDocs = {
    implicit val formats = DefaultFormats
    val query = new Query[String, Map[String, JValue]](this, this / "_all_docs")
    query()
  }

}
