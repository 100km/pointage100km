package net.rfc1149.canape

import dispatch._
import dispatch.liftjson.Js._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._

/**
 * Connexion to a CouchDB server.
 *
 * @param host the server host name or IP address
 * @param port the server port
 * @param auth an optional (login, password) pair
 */

class Couch(val host: String,
	    val port: Int,
	    private val auth: Option[(String, String)]) {

  /** URI that refers to the database */
  private[canape] val uri = "http://" + auth.map(x => x._1 + ":" + x._2 + "@").getOrElse("") + host + ":" + port

  protected def canEqual(that: Any) = that.isInstanceOf[Couch]

  override def equals(that: Any) = that match {
      case other: Couch if other.canEqual(this) => uri == other.uri
      case _                                    => false
  }

  override def hashCode() = toString.hashCode()

  override def toString =
    "http://" + auth.map(x => x._1 + ":********@").getOrElse("") + host + ":" + port

  private[canape] val couchRequest = {
    val base = :/(host, port) <:< Map("Accept" -> "application/json")
    auth match {
      case Some((login, password)) => base.as_!(login, password)
      case None                    => base
    }
  }

  /**
   * Launch a mono-directional replication.
   *
   * @param source the database to replicate from
   * @param target the database to replicate into
   * @param continuous true if the replication must be continuous, false otherwise
   *
   * @throws StatusCode if an error occurs
   */
  def replicate(source: Database, target: Database, continuous: Boolean): Handler[Unit] = {
    val params = ("source" -> source.uriFrom(this)) ~
                 ("target" -> target.uriFrom(this)) ~
                 ("continuous" -> continuous)
    couchRequest / "_replicate" << (compact(render(params)), "application/json") >|
  }

  /**
   * CouchDB installation status.
   *
   * @return the status as a Handler
   */
  def status(): Handler[Couch.Status] = couchRequest ># (new Couch.Status(_))


  /**
   * CouchDB active tasks.
   *
   * @return the list of active tasks as a JSON objects list in a Handler
   * @throws StatusCode if an error occurs
   */
  def activeTasks(): Handler[List[JObject]] =
    (couchRequest / "_active_tasks") ># { js: JValue =>
      implicit val f = DefaultFormats
      js.extract[List[JObject]]
    }

  /**
   * Get a named database. This does not attempt to connect to the database or check
   * its existence.
   *
   * @return an object representing this database.
   */
  def db(databaseName: String) = Database(this, databaseName)

}

object Couch {

  /**
   * Create a Couch instance with sensible defaults.
   *
   * @param host the server host name or IP address
   * @param port the server port
   * @param auth an optional (login, password) pair
   * @return a Couch instance
   */
  def apply(host: String = "localhost", port: Int = 5984, auth: Option[(String, String)] = None): Couch =
    new Couch(host, port, auth)

  /**
   * Create a Couch instance.
   *
   * @param host the server host name or IP address
   * @param port the server port
   * @param login the login to use
   * @param password the login to use
   * @return a Couch instance
   */
  def apply(host: String, port: Int, login: String, password: String): Couch =
    Couch(host, port, Some((login, password)))

  /**
   * Create a Couch instance with sensible defaults for host and port.
   *
   * @param login the login to use
   * @param password the login to use
   * @return a Couch instance
   */
  def apply(login: String, password: String): Couch =
    Couch(auth = Some((login, password)))

  /**
   * Create a Couch instance with a sensible default for the port.
   *
   * @param host the server host name or IP address
   * @param login the login to use
   * @param password the login to use
   * @return a Couch instance
   */
  def apply(host: String, login: String, password: String): Couch =
    Couch(host, auth = Some((login, password)))

  /** The Couch instance current status. */
  class Status(js: JValue) {

    private implicit val formats = DefaultFormats

    val JString(couchdb) = js \ "couchdb"
    val JString(version) = js \ "version"
    val vendorVersion = (js \ "vendor" \ "version").extractOpt[String]
    val vendorName = (js \ "vendor" \ "name").extractOpt[String]

  }

}
