package net.rfc1149.canape

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.marshalling.{Marshal, ToEntityMarshaller}
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import net.ceedubs.ficus.Ficus._
import play.api.libs.json._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.util.Try

/**
 * Connexion to a CouchDB server.
 *
 * @param host the server host name or IP address
 * @param port the server port
 * @param auth an optional (login, password) pair
 * @param secure use HTTPS instead of HTTP
 * @param config alternate configuration to use
 * @note HTTPS does not work with virtual servers using SNI with Akka 2.4.2-RC2,
 *       see [[https://github.com/akka/akka/issues/19287#issuecomment-183680774]].
 */

class Couch(
    val host: String = "localhost",
    val port: Int = 5984,
    val auth: Option[(String, String)] = None,
    val secure: Boolean = false,
    val config: Config = ConfigFactory.load()
)(implicit private[canape] val system: ActorSystem) extends PlayJsonSupport {

  import Couch._

  private[canape] implicit val dispatcher = system.dispatcher
  private[canape] implicit val fm = ActorMaterializer()

  val canapeConfig: Config = config.getConfig("canape")
  private[this] val userAgent = `User-Agent`(canapeConfig.as[String]("user-agent"))
  private[this] implicit val timeout: Timeout = canapeConfig.as[FiniteDuration]("request-timeout")

  private[this] def createPool(settings: ConnectionPoolSettings): Flow[(HttpRequest, Any), (Try[HttpResponse], Any), HostConnectionPool] = {
    val pool =
      if (secure)
        Http().newHostConnectionPoolHttps[Any](host, port, settings = settings)
      else
        Http().newHostConnectionPool[Any](host, port, settings = settings)
    Flow[(HttpRequest, Any)].viaMat(pool)(Keep.right)
  }

  private[this] lazy val hostConnectionPool: Flow[(HttpRequest, Any), (Try[HttpResponse], Any), HostConnectionPool] =
    createPool(ConnectionPoolSettings(config))

  private[this] val blockingHostConnectionFlow = {
    val clientConnectionSettings = ClientConnectionSettings(config).withIdleTimeout(Duration.Inf)
    if (secure)
      Http().outgoingConnectionHttps(host, port, settings = clientConnectionSettings)
    else
      Http().outgoingConnection(host, port, settings = clientConnectionSettings)
  }

  /**
   * Send an arbitrary HTTP request on the regular (non-blocking) pool.
   *
   * @param request the request to send
   */
  def sendRequest(request: HttpRequest): Future[HttpResponse] =
    Source.single(request → NotUsed).via(hostConnectionPool).runWith(Sink.head).map(_._1.get)

  /**
   * Send an arbitrary HTTP request on the potentially blocking bool.
   *
   * @param request the request to send
   */
  def sendPotentiallyBlockingRequest(request: Future[HttpRequest]): Source[HttpResponse, NotUsed] = {
    Source.fromFuture(request).map { r ⇒
      if (r.method != HttpMethods.POST)
        throw new IllegalArgumentException("potentially blocking request must use POST method")
      r
    }.via(blockingHostConnectionFlow)
  }

  private[this] val defaultHeaders = {
    val authHeader = auth map { case (login, password) ⇒ Authorization(BasicHttpCredentials(login, password)) }
    userAgent :: Accept(`application/json`) :: authHeader.toList
  }

  private[this] def Get(query: Uri): HttpRequest = HttpRequest(GET, uri = query, headers = defaultHeaders)

  private[canape] def Post[T: ToEntityMarshaller](query: Uri, data: T): Future[HttpRequest] =
    Marshal(data).to[RequestEntity].map(e ⇒ HttpRequest(POST, uri = query, entity = e, headers = defaultHeaders))

  private[this] def Put[T: ToEntityMarshaller](query: Uri, data: T = HttpEntity.Empty): Future[HttpRequest] =
    Marshal(data).to[RequestEntity].map(e ⇒ HttpRequest(PUT, uri = query, entity = e, headers = defaultHeaders))

  private[this] def Delete(query: Uri): HttpRequest =
    HttpRequest(DELETE, uri = query, headers = defaultHeaders)

  /**
   * Build a GET HTTP request.
   *
   * @param query the query string, including the already-encoded optional parameters
   * @return a future containing the HTTP response
   */
  def makeRawGetRequest(query: Uri): Future[HttpResponse] = sendRequest(Get(query))

  /**
   * Build a GET HTTP request.
   *
   * @param query the query string, including the already-encoded optional parameters
   * @tparam T the type of the result
   * @return a future containing the required result
   */
  def makeGetRequest[T: Reads](query: Uri): Future[T] =
    makeRawGetRequest(query).flatMap(checkResponse[T])

  /**
   * Build a POST HTTP request.
   *
   * @param query the query string, including the already-encoded optional parameters
   * @param data the data to post
   * @tparam T the type of the result
   * @return a future containing the required result
   * @throws CouchError if an error occurs
   */
  def makePostRequest[T: Reads](query: Uri, data: JsObject): Future[T] =
    Post(query, data).flatMap(sendRequest).flatMap(checkResponse[T])

  /**
   * Build a POST HTTP request.
   *
   * @param query the query string, including the already-encoded optional parameters
   * @tparam T the type of the result
   * @return A future containing the required result
   * @throws CouchError if an error occurs
   */
  def makePostRequest[T: Reads](query: Uri): Future[T] =
    Post(query, fakeEmptyJsonPayload).flatMap(sendRequest).flatMap(checkResponse[T])

  /**
   * Build a POST HTTP request.
   *
   * @param query the query string, including the already-encoded optional parameters
   * @param data the data to post
   * @return a future containing the required result
   * @throws CouchError if an error occurs
   */
  def makeRawPostRequest(query: Uri, data: FormData): Future[HttpResponse] = {
    val payload = HttpEntity(ContentType(MediaTypes.`application/x-www-form-urlencoded`, HttpCharsets.`UTF-8`), data.fields.toString())
    Post(query, payload).flatMap(sendRequest)
  }

  /**
   * Build a PUT HTTP request.
   *
   * @param query the query string, including the already-encoded optional parameters
   * @param data the data to post
   * @tparam T the type of the data
   * @return a future containing the HTTP response
   * @throws CouchError if an error occurs
   */

  def makeRawPutRequest[T: Writes](query: Uri, data: T): Future[HttpResponse] =
    Put(query, Json.toJson(data)).flatMap(sendRequest)

  /**
   * Build a PUT HTTP request.
   *
   * @param query the query string, including the already-encoded optional parameters
   * @param data the data to post
   * @tparam T the type of the result
   * @return a future containing the required result
   * @throws CouchError if an error occurs
   */
  def makePutRequest[T: Writes, R: Reads](query: Uri, data: T): Future[R] =
    makeRawPutRequest(query, data).flatMap(checkResponse[R])

  /**
   * Build a PUT HTTP request.
   *
   * @param query the query string, including the already-encoded optional parameters
   * @tparam T the type of the result
   * @return a future containing the required result
   * @throws CouchError if an error occurs
   */
  def makePutRequest[T: Reads](query: Uri): Future[T] =
    Put(query).flatMap(sendRequest).flatMap(checkResponse[T])

  /**
   * Build a DELETE HTTP request.
   *
   * @param query the query string, including the already-encoded optional parameters
   * @tparam T the type of the result
   * @return a future containing the required result
   * @throws CouchError if an error occurs
   */
  def makeDeleteRequest[T: Reads](query: Uri): Future[T] =
    sendRequest(Delete(query)).flatMap(checkResponse[T])

  private[this] def buildURI(fixedAuth: Option[(String, String)]): Uri = {
    val uri = Uri().withScheme(if (secure) "https" else "http").withHost(host).withUserInfo(fixedAuth.map(u ⇒ s"${u._1}:${u._2}").getOrElse(""))
    if ((!secure && port == 80) || (secure && port == 443))
      uri
    else
      uri.withPort(port)
  }

  /** URI that refers to the database */
  val uri: Uri = buildURI(auth)

  protected def canEqual(that: Any) = that.isInstanceOf[Couch]

  override def equals(that: Any) = that match {
    case other: Couch if other.canEqual(this) ⇒ uri == other.uri
    case _                                    ⇒ false
  }

  override def hashCode() = toString.hashCode()

  override def toString = buildURI(auth.map(x ⇒ (x._1, "********"))).toString()

  /**
   * Launch a mono-directional replication.
   *
   * @param source the database to replicate from
   * @param target the database to replicate into
   * @param params extra parameters to the request
   * @return a request
   * @throws CouchError if an error occurs
   */
  def replicate(source: Database, target: Database, params: JsObject = Json.obj()): Future[JsObject] = {
    makePostRequest[JsObject]("/_replicate", params ++ Json.obj("source" → source.uriFrom(this), "target" → target.uriFrom(this)))
  }

  /**
   * CouchDB installation status.
   *
   * @return a request
   * @throws CouchError if an error occurs
   */
  def status(): Future[Status] = makeGetRequest[Status]("/")

  /**
   * CouchDB active tasks.
   *
   * @return a request
   * @throws CouchError if an error occurs
   */
  def activeTasks(): Future[List[JsObject]] = makeGetRequest[List[JsObject]]("/_active_tasks")

  /**
   * Request UUIDs from the database.
   *
   * @param count the number of UUIDs to return
   * @return a sequence of UUIDs
   */
  def getUUIDs(count: Int): Future[Seq[String]] =
    makeGetRequest[JsObject](s"/_uuids?count=$count") map { r ⇒ (r \ "uuids").as[Seq[String]] }

  /**
   * Request an UUID from the database.
   *
   * @return an UUID
   */
  def getUUID: Future[String] = getUUIDs(1).map(_.head)

  /**
   * Get a named database. This does not attempt to connect to the database or check
   * its existence.
   *
   * @param databaseName the database name
   * @return an object representing this database
   */
  def db(databaseName: String) = Database(this, databaseName)

  /**
   * Get the list of existing databases.
   *
   * @return a list of databases on this server
   */
  def databases(): Future[List[String]] = makeGetRequest[List[String]]("/_all_dbs")

  /**
   * Release external resources used by this connector.
   *
   * @return a future which gets completed when the release is done
   */
  def releaseExternalResources(): Future[Unit] =
    Http().shutdownAllConnectionPools()

  lazy val isCouchDB1: Future[Boolean] = status().map(_.version.startsWith("1."))

}

object Couch extends PlayJsonSupport {

  def statusErrorFromResponse(response: HttpResponse)(implicit fm: Materializer, ec: ExecutionContext): Future[Nothing] = {
    Unmarshal(response.entity).to[JsObject].map(new StatusError(response.status, _))
      .fallbackTo(FastFuture.successful(new StatusError(response.status))) // Do not fail in cascade for a non CouchDB JS response
      .map(throw _)
  }

  def maybeConsumeBody(response: HttpResponse, keepBody: Boolean)(implicit fm: Materializer): HttpResponse = {
    if (!keepBody)
      response.entity.dataBytes.runWith(Sink.ignore)
    response
  }

  /**
   * Check the status of the HTTP response and throw an exception if it is a failure.
   *
   * @param response the response to check
   * @return the response itself if successfull
   * @throws StatusError if the response is a failure
   */
  def checkStatus(response: HttpResponse): HttpResponse = {
    if (response.status.isFailure())
      throw new StatusError(response.status)
    response
  }

  /**
   * Unmarshal a HTTP Json response after checking its status code.
   *
   * @param response the HTTP response
   * @param fm a flow materializer
   * @param ec an execution context
   * @tparam T the type of the response
   * @return the decoded response
   * @throws StatusError if the response is a failure
   */
  def checkResponse[T: Reads](response: HttpResponse)(implicit fm: Materializer, ec: ExecutionContext): Future[T] = {
    response.status match {
      case status if status.isFailure() ⇒
        statusErrorFromResponse(response)
      case _ ⇒
        Unmarshal(response.entity).to[T]
    }
  }

  sealed abstract class CouchError extends Exception

  case class DataError(error: JsError) extends CouchError

  case class StatusError(code: Int, error: String, reason: String) extends CouchError {

    def this(status: StatusCode, body: JsObject) =
      this(status.intValue(), (body \ "error").as[String], (body \ "reason").as[String])

    def this(status: StatusCode) =
      this(status.intValue(), status.defaultMessage(), status.reason())

    override def toString = s"StatusError($code, $error, $reason)"

    override def getMessage = s"$code $reason: $error"

  }

  // Because of bug COUCHDB-2583, some methods require an empty payload with content-type
  // `application/json`, which is invalid. We will generate it anyway to be compatible
  // with CouchDB 1.6.1.
  private[canape] val fakeEmptyJsonPayload = HttpEntity(`application/json`, "")

  /**The Couch instance current status. */
  case class Status(
    couchdb: String,
    version: String,
    vendor: Option[VendorInfo]
  )

  case class VendorInfo(
    name: String,
    version: Option[String]
  )

  implicit val vendorInfoRead: Reads[VendorInfo] = Json.reads[VendorInfo]
  implicit val statusRead: Reads[Status] = Json.reads[Status]

}
