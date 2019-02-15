package net.rfc1149.canape

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.Uri.{Path, Query}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.ETag
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.{Flow, Framing, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import net.ceedubs.ficus.Ficus._
import play.api.libs.json._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}

case class Database(couch: Couch, databaseName: String) {

  import Couch._
  import Database._
  import couch.fm

  private[canape] implicit val dispatcher = couch.dispatcher

  private[this] val localPath: Path = Path(s"/$databaseName")
  val uri: Uri = couch.uri.withPath(localPath)

  override def toString = uri.toString()

  override def hashCode = uri.hashCode

  override def canEqual(that: Any) = that.isInstanceOf[Database]

  override def equals(that: Any): Boolean = that match {
    case other: Database if other.canEqual(this) ⇒ uri == other.uri
    case _                                       ⇒ false
  }

  def uriFrom(other: Couch): String = if (couch == other) databaseName else uri.toString()

  private def encode(extra: String, properties: Seq[(String, String)] = Seq()): Uri = {
    val components = extra.split('/')
    val base = Uri().withPath(components.foldLeft(localPath)(_ / _))
    if (properties.isEmpty) base else base.withQuery(Query(properties.toMap))
  }

  /**
   * Get the database status.
   *
   * @return a request
   */
  def status(): Future[JsObject] = couch.makeGetRequest[JsObject](encode(""))

  /**
   * Get the latest revision of an existing document from the database.
   *
   * @param id the id of the document
   * @return a request
   * @throws CouchError if an error occurs
   */
  def apply(id: String): Future[JsObject] =
    couch.makeGetRequest[JsObject](encode(id))

  /**
   * Get a particular revision of an existing document from the database.
   *
   * @param id the id of the document
   * @param rev the revision of the document
   * @return a request
   * @throws CouchError if an error occurs
   */
  def apply(id: String, rev: String): Future[JsObject] =
    couch.makeGetRequest[JsObject](encode(id, Seq("rev" → rev)))

  /**
   * Get an existing document from the database.
   *
   * @param id the id of the document
   * @param properties the properties to add to the request
   * @return a request
   * @throws CouchError if an error occurs
   */
  def apply(id: String, properties: Map[String, String]): Future[JsValue] =
    apply(id, properties.toSeq)

  /**
   * Get an existing document from the database.
   *
   * @param id the id of the document
   * @param properties the properties to add to the request
   * @return a request
   * @throws CouchError if an error occurs
   */
  def apply(id: String, properties: Seq[(String, String)]): Future[JsValue] =
    couch.makeGetRequest[JsValue](encode(id, properties))

  private[this] def query(id: String, properties: Seq[(String, String)]): Future[Result] =
    couch.makeGetRequest[Result](encode(id, properties))

  /**
   * Query a view from the database but prevent the reduce part from running.
   *
   * @param design the design document
   * @param name the name of the view
   * @param properties the properties to add to the request
   * @return a future containing the result
   * @throws CouchError if an error occurs
   */
  def mapOnly(design: String, name: String, properties: Seq[(String, String)] = Seq()): Future[Result] =
    query(s"_design/$design/_view/$name", properties :+ ("reduce" → "false"))

  /**
   * Query a view from the database using map/reduce.
   *
   * @param design the design document
   * @param name the name of the view
   * @param properties the properties to add to the request
   * @tparam V the value type
   * @return a future containing a sequence of results
   */
  def view[K: Reads, V: Reads](design: String, name: String, properties: Seq[(String, String)] = Seq()): Future[Seq[(K, V)]] =
    couch.makeGetRequest[JsObject](encode(s"_design/$design/_view/$name", properties)).map(result ⇒ (result \ "rows").as[Array[JsValue]] map { row ⇒
      (row \ "key").as[K] → (row \ "value").as[V]
    })

  /**
   * Query a view from the database using map/reduce and include the update sequence number.
   *
   * @param design the design document
   * @param name the name of the view
   * @param properties the properties to add to the request
   * @tparam V the value type
   * @return a future containing the update sequence and a sequence of results
   */
  def viewWithUpdateSeq[K: Reads, V: Reads](design: String, name: String, properties: Seq[(String, String)] = Seq()): Future[(UpdateSequence, Seq[(K, V)])] =
    couch.makeGetRequest[JsObject](encode(s"_design/$design/_view/$name", properties :+ ("update_seq" → "true"))).map(result ⇒
      ((result \ "update_seq").as[UpdateSequence],
        (result \ "rows").as[Array[JsValue]] map (row ⇒ (row \ "key").as[K] → (row \ "value").as[V])))

  /**
   * Query a list from the database.
   *
   * @param design the design document
   * @param list the name of the list
   * @param view the name of the view whose result will be passed to the list
   * @param properties the properties to add to the request
   * @param keepBody do not consume the entity body (default is to consume it to prevent stalling the connection)
   * @return a future containing a HTTP response
   */
  def list(design: String, list: String, view: String, properties: Seq[(String, String)] = Seq(), keepBody: Boolean = false): Future[HttpResponse] =
    couch.makeRawGetRequest(encode(s"_design/$design/_list/$list/$view", properties)).map(Couch.maybeConsumeBody(_, keepBody))

  /**
   * Call an update function.
   *
   * @param design the design document
   * @param name the name of the update function
   * @param data the data to pass to the update function as form data
   * @param keepBody do not consume the entity body (default is to consume it to prevent stalling the connection)
   * @return a future containing a HTTP response
   * @throws CouchError if an error occurs
   */
  def updateForm(design: String, name: String, id: String, data: Map[String, String], keepBody: Boolean = false): Future[HttpResponse] =
    couch.makeRawPostRequest(encode(s"_design/$design/_update/$name/$id"), FormData(data)).map(Couch.maybeConsumeBody(_, keepBody))

  /**
   * Call an update function.
   *
   * @param design the design document
   * @param name the name of the update function
   * @param data the data to pass to the update function in the body as Json
   * @param keepBody do not consume the entity body (default is to consume it to prevent stalling the connection)
   * @return a future containing a HTTP response
   * @throws CouchError if an error occurs
   */
  def updateBody[T: Writes](design: String, name: String, id: String, data: T, keepBody: Boolean = false): Future[HttpResponse] =
    couch.makeRawPutRequest[T](encode(s"_design/$design/_update/$name/$id"), data).map(Couch.maybeConsumeBody(_, keepBody))

  /**
   * Retrieve the list of public documents from the database.
   *
   * @return a request
   * @throws CouchError if an error occurs
   */
  def allDocs(): Future[Result] = allDocs(Map())

  /**
   * Retrieve the list of public documents from the database.
   *
   * @param params the properties to add to the request
   * @return a request
   * @throws CouchError if an error occurs
   */
  def allDocs(params: Map[String, String]): Future[Result] =
    query("_all_docs", params.toSeq)

  /**
   * Create the database.
   *
   * @return a request
   * @throws CouchError if an error occurs
   */
  def create(): Future[JsValue] = couch.makePutRequest[JsValue](encode(""))

  /**
   * Compact the database.
   *
   * @return a request
   * @throws CouchError if an error occurs
   */
  def compact(): Future[JsValue] = couch.makePostRequest[JsValue](encode("_compact"))

  /**
   * Insert documents in bulk mode.
   *
   * @param docs the documents to insert
   * @param allOrNothing force an insertion of all documents (CouchDB 1.x only)
   * @return a request
   * @throws CouchError if an error occurs
   */
  def bulkDocs(docs: Seq[JsObject], allOrNothing: Boolean = false): Future[Seq[JsObject]] = {
    checkAllOrNothing(allOrNothing) {
      val args = Json.obj("all_or_nothing" → JsBoolean(allOrNothing), "docs" → docs)
      couch.makePostRequest[Seq[JsObject]](encode("_bulk_docs"), args)
    }
  }

  /**
   * Insert a document into the database.
   *
   * @param doc the document to insert
   * @param id the id of the document if it is known and absent from the document itself
   * @param batch allow the insertion in batch (unchecked) mode
   * @param newEdits set to false if you want to be able to create conflicting documents (id needs to be provided and a
   *                 "_rev" well-formed value must be present)
   * @return the answer from the database
   * @throws CouchError if an error occurs
   */
  def insert(doc: JsObject, id: String = null, batch: Boolean = false, newEdits: Boolean = true): Future[JsValue] = {
    val batchParams: Seq[(String, String)] = if (batch) Seq(("batch", "ok")) else Seq()
    if (id == null) {
      require(newEdits, "newEdits=false is only supported with an explicit id")
      couch.makePostRequest[JsValue](encode("", batchParams), doc)
    } else {
      val newEditsParam: Seq[(String, String)] = if (newEdits) Seq() else Seq(("new_edits", "false"))
      couch.makePutRequest[JsObject, JsValue](encode(id, batchParams ++ newEditsParam), doc)
    }
  }

  /**
   * Return the latest revision of a document.
   *
   * @param id the id of the document
   * @return the revision
   */
  def latestRev(id: String): Future[String] =
    couch.sendRequest(RequestBuilding.Head(encode(id))).map {
      case response if response.status.isSuccess ⇒
        response.entity.discardBytes()
        response.header[ETag].map(_.value()).get.stripPrefix("\"").stripSuffix("\"")
      case response ⇒
        response.entity.discardBytes()
        throw new StatusError(response.status)
    }

  /**
   * Delete a document from the database.
   *
   * @param id the id of the document
   * @param rev the revision to delete
   * @return the revision representing the deletion
   * @throws CouchError if an error occurs
   */
  def delete(id: String, rev: String): Future[String] =
    couch.makeDeleteRequest[JsValue](encode(id, Seq("rev" → rev))).map(js ⇒ (js \ "rev").as[String])

  /**
   * Delete multiple revisions of a document from the database. There will be no error if the document does not exist.
   *
   * @param id the id of the document
   * @param revs the revisions to delete (may be empty)
   * @param allOrNothing `true` if all deletions must succeed or fail atomically (CouchDB 1.x only)
   * @return a list of revisions that have been successfully deleted
   */
  def delete(id: String, revs: Seq[String], allOrNothing: Boolean = false): Future[Seq[String]] =
    checkAllOrNothing(allOrNothing) {
      revs match {
        case Nil ⇒
          FastFuture.successful(Nil)
        case revs@(rev :: Nil) ⇒
          delete(id, rev).map(_ ⇒ revs).recover { case _ ⇒ Seq() }
        case _ ⇒
          bulkDocs(revs.map(rev ⇒ Json.obj("_id" → id, "_rev" → rev, "_deleted" → true)), allOrNothing = allOrNothing)
            .map(revs.zip(_) collect { case (rev, js) if !js.keys.contains("error") ⇒ rev })
      }
    }

  /**
   * Delete a document from the database.
   *
   * @param doc the document which must contains an `_id` and a `_rev` field
   * @return the revision representing the deletion
   * @throws CouchError if an error occurs
   */
  def delete[T](doc: JsObject): Future[String] = {
    val id = (doc \ "_id").as[String]
    val rev = (doc \ "_rev").as[String]
    delete(id, rev)
  }

  /**
   * Delete the latest version of a document from the database.
   *
   * @param id the id of the document
   * @return the revision representing the deletion
   */
  def delete(id: String): Future[String] =
    latestRev(id).flatMap(delete(id, _))

  /**
   * Delete all revisions of a document from the database. There will be no error if the document does not exist.
   *
   * @param id the id of the document
   * @return a sequence of all deleted revisions
   */
  def deleteAll(id: String): Future[Seq[String]] = {
    this(id, Seq("conflicts" → "true")).map(doc ⇒ (doc \ "_rev").as[String] :: (doc \ "_conflicts").asOpt[List[String]].getOrElse(Nil)) flatMap {
      delete(id, _)
    }
  }

  /**
   * Use Mango to retrieve documents from the database.
   *
   * @param query the full query with selector and options
   * @return the documents
   */
  def find(query: JsObject): Future[Seq[JsObject]] =
    couch.makePostRequest[JsObject](encode("_find"), query).map(js ⇒ (js \ "docs").as[Seq[JsObject]])

  /**
   * Create a Mango index.
   *
   * @param query the full query with index creation and options
   * @return the result from the database
   */
  def index(query: JsObject): Future[JsObject] =
    couch.makePostRequest[JsObject](encode("_index"), query)

  /**
   * Explain a Mango query.
   *
   * @param query the full query with selector and options
   * @return the explanation
   */
  def explain(query: JsObject): Future[JsObject] =
    couch.makePostRequest[JsObject](encode("_explain"), query)

  /**
   * Delete the database.
   *
   * @return a request
   * @throws CouchError if an error occurs
   */
  def delete(): Future[JsValue] = couch.makeDeleteRequest[JsValue](encode(""))

  /**
   * Request the list of changes from the database in a non-continuous way.
   *
   * @param params the parameters to add to the request
   * @param extraParams the extra parameters to add to the request, such as a long list of doc ids, or `null`
   * @return a future with the only change
   */
  def changes(params: Map[String, String] = Map(), extraParams: JsObject = Json.obj()): Future[JsValue] =
    couch.sendPotentiallyBlockingRequest(couch.Post(encode("_changes", params.toSeq), extraParams))
      .runWith(Sink.head).flatMap(checkResponse[JsValue])

  def revs_limit(limit: Int): Future[JsValue] =
    couch.makePutRequest[Int, JsValue](encode("_revs_limit"), limit)

  def revs_limit(): Future[Long] =
    couch.makeGetRequest[Long](encode("_revs_limit"))

  /**
   * Ensure that the database has been written to the permanent storage.
   *
   * @return a request
   * @throws CouchError if an error occurs
   */
  def ensureFullCommit(): Future[JsValue] =
    couch.makePostRequest[JsValue](encode("_ensure_full_commit"))

  /**
   * Launch a mono-directional replication from another database.
   *
   * @param source the database to replicate from
   * @param params extra parameters to the request
   * @return a request
   * @throws CouchError if an error occurs
   */
  def replicateFrom(source: Database, params: JsObject = Json.obj()): Future[JsObject] =
    couch.replicate(source, this, params)

  /**
   * Launch a mono-directional replication to another database.
   *
   * @param target the database to replicate to
   * @param params extra parameters to the request
   * @return a request
   * @throws CouchError if an error occurs
   */
  def replicateTo(target: Database, params: JsObject = Json.obj()): Future[JsObject] =
    couch.replicate(this, target, params)

  /**
   * Return a one-time continuous changes stream.
   *
   * @param params the additional parameters to the request
   * @param extraParams the extra parameters to the request such as a long list of doc ids
   * @return a source containing the changes as well as the termination marker when the connection closes without error,
   *         the materialized value contains Done or an error if the HTTP request was unsuccesful
   */
  def continuousChanges(params: Map[String, String] = Map(), extraParams: JsObject = Json.obj()): Source[JsObject, Future[Done]] = {
    val promise = Promise[Done]
    val requestParams = {
      val heartBeatParam = (params.get("timeout"), params.get("heartbeat")) match {
        case (Some(_), Some(h)) if h.nonEmpty ⇒ Map("heartbeat" → h) // Timeout will be ignored by the DB, but the user has chosen
        case (Some(_), _)                     ⇒ Map() // Use provided timeout only
        case (None, Some(""))                 ⇒ Map() // Disable heartbeat
        case (None, Some(h))                  ⇒ Map("heartbeat" → h) // Use provided heartbeat
        case (None, None) ⇒ Map("heartbeat" → // Use default heartbeat from configuration
          couch.canapeConfig.as[FiniteDuration]("continuous-changes.heartbeat-interval").toMillis.toString)
      }
      params - "heartbeat" ++ heartBeatParam
    }
    // CouchDB 2.0.0-RC4 does not allow a proper empty JSON payload and require an empty (though invalid) payload
    // to be sent.
    val request = if (extraParams.keys.isEmpty)
      couch.Post(encode("_changes", (requestParams + ("feed" → "continuous")).toSeq), Couch.fakeEmptyJsonPayload)
    else
      couch.Post(encode("_changes", (requestParams + ("feed" → "continuous")).toSeq), extraParams)
    couch.sendPotentiallyBlockingRequest(request)
      .mapError {
        case t ⇒
          // The promise might have been already completed if the error happens after the connection
          promise.tryFailure(t)
          t
      }
      .flatMapConcat {
        case response if response.status.isSuccess() ⇒
          promise.success(Done)
          response.entity.dataBytes.via(filterJson)
        case response ⇒
          val error = statusErrorFromResponse(response)
          promise.completeWith(error)
          Source.fromFuture(error)
      }
      .mapMaterializedValue(_ ⇒ promise.future)
  }

  /**
   * Return a one-time continuous changes stream.
   *
   * @param docIds the document ids to watch
   * @param params the additional parameters to the request
   * @param extraParams the extra parameters to the request (passed in the body)
   * @return a source containing the changes as well as the termination marker when the connection closes without error,
   *         the materialized value contains Done or an error if the HTTP request was unsuccesful
   */
  def continuousChangesByDocIds(docIds: Seq[String], params: Map[String, String] = Map(), extraParams: JsObject = Json.obj()): Source[JsObject, Future[Done]] =
    continuousChanges(params + ("filter" → "_doc_ids"), extraParams ++ Json.obj("doc_ids" → docIds))

  /**
   * Return a continuous changes stream.
   *
   * @param params the additional parameters to the request
   * @param extraParams the extra parameters to the request such as a long list of doc ids
   * @param sinceSeq the latest uninteresting sequence number, or the current database state if not provided
   * @return a source containing the changes, materialized as a Done object when connected for the first time
   *         to the database with a successful HTTP response code
   */
  def changesSource(params: Map[String, String] = Map(), extraParams: JsObject = Json.obj(),
    sinceSeq: UpdateSequence = FromNow): Source[JsObject, Future[Done]] = {
    ChangesSource.changesSource(this, params, extraParams, sinceSeq)
  }

  /**
   * Return a continuous changes stream.
   *
   * @param docIds the document ids to watch
   * @param params the additional parameters to the request
   * @param extraParams the extra parameters to the request (passed in the body)
   * @param sinceSeq the latest uninteresting sequence number, or the current database state if not provided
   * @return a source containing the changes, materialized as a Done object when connected for the first time
   *         to the database with a successful HTTP response code
   */
  def changesSourceByDocIds(docIds: Seq[String], params: Map[String, String] = Map(), extraParams: JsObject = Json.obj(),
    sinceSeq: UpdateSequence = FromNow): Source[JsObject, Future[Done]] =
    changesSource(params + ("filter" → "_doc_ids"), extraParams ++ Json.obj("doc_ids" → docIds), sinceSeq)

  /**
   * Reject the current request with `IllegalArgumentException` if `allOrNothing` is `true` and the CouchDB
   * version is not 1.x.
   *
   * @param allOrNothing the parameter to check
   * @param action the action to perform if allowed
   * @tparam T the type of the result
   * @return the result, or `IllegalArgumentException` if not allowed
   */
  private def checkAllOrNothing[T](allOrNothing: Boolean)(action: ⇒ Future[T]): Future[T] =
    couch.isCouchDB1.flatMap { isCouchDB1 ⇒
      require(isCouchDB1 || !allOrNothing, "allOrNothing cannot only be used with CouchDB 1.x")
      action
    }

}

object Database {

  case class ChangedError(status: akka.http.scaladsl.model.StatusCode) extends Exception

  /**
   * Filter objects that contains a `seq` field. To be used with [[Database#continousChanges]].
   */
  val onlySeq: Flow[JsObject, JsObject, NotUsed] = Flow[JsObject].filter(_.keys.contains("seq"))

  private val filterJson: Flow[ByteString, JsObject, NotUsed] =
    Flow[ByteString]
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 1000000))
      .map(_.utf8String)
      .filter(_.length > 1)
      .map(Json.parse(_).as[JsObject])

  sealed trait UpdateSequence {
    def toLong: Long
  }

  final case class UpdateSequenceString(override val toString: String) extends UpdateSequence {
    def toLong: Long = toString.split("-", 2).head.toLong
  }

  final case class UpdateSequenceLong(toLong: Long) extends UpdateSequence {
    override val toString = toLong.toString
  }

  object UpdateSequence {
    def apply(js: JsValue): UpdateSequence = js match {
      case JsNumber(n) ⇒ UpdateSequenceLong(n.toLongExact)
      case JsString(s) ⇒ UpdateSequenceString(s)
      case _           ⇒ throw new IllegalArgumentException("update sequence must be number or string")
    }
  }

  implicit val updateSequenceReads: Reads[UpdateSequence] = Reads {
    case JsNumber(n) ⇒ JsSuccess(UpdateSequenceLong(n.toLongExact))
    case JsString(s) ⇒ JsSuccess(UpdateSequenceString(s))
    case _           ⇒ JsError("update sequence must be number or string")
  }

  val FromNow = UpdateSequenceString("now")
  val FromStart = UpdateSequenceLong(0)

}
