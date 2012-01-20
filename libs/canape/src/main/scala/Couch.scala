package net.rfc1149.canape

import dispatch._
import dispatch.liftjson.Js._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._

case class Couch(val host: String = "localhost",
		 val port: Int = 5984,
		 val auth: Option[(String, String)] = None) {

  val uri = "http://" + auth.map(x => x._1 + ":" + x._2 + "@").getOrElse("") + host + ":" + port

  private[canape] val couchRequest = {
    val base = :/(host, port) <:< Map("Accept" -> "application/json")
    auth match {
      case Some((login, password)) => base.as_!(login, password)
      case None                    => base
    }
  }

  def replicate(source: Database, target: Database, continuous: Boolean): Handler[Unit] = {
    val params = ("source" -> source.uriFrom(this)) ~
                 ("target" -> target.uriFrom(this)) ~
                 ("continuous" -> continuous)
    couchRequest / "_replicate" << (compact(render(params)), "application/json") >|
  }

  def status(): Handler[Couch.Status] = couchRequest ># (new Couch.Status(_))

  def activeTasks(): Handler[List[JObject]] =
    (couchRequest / "_active_tasks") ># { js: JValue =>
      implicit val f = DefaultFormats
      js.extract[List[JObject]]
    }

  def db(databaseName: String) = Database(this, databaseName)

}

object Couch {

  def apply(host: String, port: Int, login: String, password: String): Couch =
    Couch(host, port, Some((login, password)))

  def apply(login: String, password: String): Couch =
    Couch(auth = Some((login, password)))

  def apply(host: String, login: String, password: String): Couch =
    Couch(host, auth = Some((login, password)))

  class Status(js: JValue) {

    private implicit val formats = DefaultFormats

    val JString(couchdb) = js \ "couchdb"
    val JString(version) = js \ "version"
    val vendorVersion = (js \ "vendor" \ "version").extractOpt[String]
    val vendorName = (js \ "vendor" \ "name").extractOpt[String]

  }

}
