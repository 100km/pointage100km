package net.rfc1149.canape

import net.liftweb.json._
import net.liftweb.json.Serialization.write
import org.jboss.netty.buffer._
import org.jboss.netty.handler.codec.base64.Base64
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.util.CharsetUtil

/**
 * Connexion to a CouchDB server.
 *
 * @param host the server host name or IP address
 * @param port the server port
 * @param auth an optional (login, password) pair
 */

abstract class Couch(val host: String,
                     val port: Int,
                     private val auth: Option[(String, String)]) extends HTTPBootstrap {

  import implicits._

  private lazy val authorization = {
    val authChannelBuffer = ChannelBuffers.copiedBuffer(auth.get._1 + ":" + auth.get._2,
							CharsetUtil.UTF_8)
    val encodedAuthChannelBuffer = Base64.encode(authChannelBuffer)
    "Basic " + encodedAuthChannelBuffer.toString(CharsetUtil.UTF_8)
  }

  private[this] def makeRequest[T: Manifest](query: String, method: HttpMethod, data: Option[Either[AnyRef, String]], allowChunks: Boolean): CouchRequest[T] = {
    val request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
					 method,
					 "/" + query)
    request.setHeader(HttpHeaders.Names.HOST, host)
    request.setHeader(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP)
    request.setHeader(HttpHeaders.Names.ACCEPT, "application/json")
    auth foreach { case (login, password) =>
      request.setHeader(HttpHeaders.Names.AUTHORIZATION, authorization)
    }
    data foreach { d =>
      val cb = ChannelBuffers.copiedBuffer(d.fold({
	case None  => ""
	case other => write(other)
       }, {s => s}), CharsetUtil.UTF_8)
      request.setHeader(HttpHeaders.Names.CONTENT_LENGTH, cb.readableBytes())
      request.setHeader(HttpHeaders.Names.CONTENT_TYPE,
			d.fold({_ => "application/json"},
			       {_ => "application/x-www-form-urlencoded" }))
      request.setContent(cb)
    }
    new SimpleCouchRequest[T](this, request, allowChunks)
  }

  private def convert(data: AnyRef): Some[Either[AnyRef, String]] =
    Some(data match {
      case s: String => Right(s)
      case _         => Left(data)
    })

  /**
   * Build a GET HTTP request.
   *
   * @param query The query string, including the already-encoded optional parameters.
   * @param allowChunks True if the handler is ready to handle HTTP chunks, false otherwise.
   * @tparam T The type of the chunks (if allowChunks is true) or of the result.
   * @return A request.
   */

  def makeGetRequest[T: Manifest](query: String, allowChunks: Boolean = false): CouchRequest[T] =
    makeRequest[T](query, HttpMethod.GET, None, allowChunks)

  // POST and PUT requests accept the following data:
  //   - String   => application/x-www-form-urlencoded, verbatim string payload
  //   - None     => application/json, empty payload
  //   - other    => application/json, converted json to string payload

  /**
   * Build a POST HTTP request.
   *
   * The data parameter can be one of the following:
   * <ul>
   *   <li>a String: it will be passed as-is, with type application/x-www-form-urlencoded;</li>
   *   <li>None: it will be passed as an empty payload with type application/json;</li>
   *   <li>other: after being converted to Json, it will be passed with type application/json.</li>
   * </ul>
   *
   * @param query the query string, including the already-encoded optional parameters
   * @param data the data to post
   * @tparam T the type of the result
   * @return a request.
   *
   * @throws StatusCode if an error occurs
   */
  def makePostRequest[T: Manifest](query: String, data: AnyRef): CouchRequest[T] =
    makeRequest[T](query, HttpMethod.POST, convert(data), false)

  /**
   * Build a PUT HTTP request.
   *
   * @see makePostRequest for more information on the data format.
   *
   * @param query the query string, including the already-encoded optional parameters
   * @param data the data to post
   * @tparam T the type of the result
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def makePutRequest[T: Manifest](query: String, data: AnyRef): CouchRequest[T] =
    makeRequest[T](query, HttpMethod.PUT, convert(data), false)

  /**
   * Build a DELETE HTTP request.
   *
   * @param query the query string, including the already-encoded optional parameters
   * @tparam T the type of the result
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def makeDeleteRequest[T: Manifest](query: String): CouchRequest[T] =
    makeRequest[T](query, HttpMethod.DELETE, None, false)

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

  /**
   * Launch a mono-directional replication.
   *
   * @param source the database to replicate from
   * @param target the database to replicate into
   * @param continuous true if the replication must be continuous, false otherwise
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def replicate(source: Database, target: Database, continuous: Boolean): CouchRequest[JValue] = {
    makePostRequest[JValue]("_replicate",
			    Map("source" -> source.uriFrom(this),
				"target" -> target.uriFrom(this),
				"continuous" -> continuous))
  }

  /**
   * CouchDB installation status.
   *
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def status(): CouchRequest[Couch.Status] = makeGetRequest[Couch.Status]("")


  /**
   * CouchDB active tasks.
   *
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def activeTasks(): CouchRequest[List[JValue]] = makeGetRequest[List[JValue]]("_active_tasks")

  /**
   * Get a named database. This does not attempt to connect to the database or check
   * its existence.
   *
   * @return an object representing this database
   */
  def db(databaseName: String) = Database(this, databaseName)

}

object Couch {

  /** The Couch instance current status. */
  case class Status(couchdb: String,
		    version: String,
		    vendor: Option[VendorInfo])

  case class VendorInfo(name: String,
			version: String)

}
