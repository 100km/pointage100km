package net.rfc1149.canape

import net.liftweb.json._
import org.jboss.netty.handler.codec.http._

case class Database(couch: Couch, database: String) {

  private[canape] val uri = couch.uri + "/" + database

  override def toString = couch.toString + "/" + database

  override def hashCode = uri.hashCode

  override def canEqual(that: Any) = that.isInstanceOf[Database]

  override def equals(that: Any): Boolean = that match {
    case other: Database if other.canEqual(this) => uri == other.uri
    case _ => false
  }

  private[canape] def uriFrom(other: Couch) = if (couch == other) database else uri

  private[this] def encode(extra: String, properties: Seq[(String, String)] = Seq()) = {
    val encoder = new QueryStringEncoder(database + "/" + extra)
    properties foreach {
      case (name, value) => encoder.addParam(name, value)
    }
    encoder.toString
  }

  /**
   * Get the database status.
   *
   * @return a request
   */
  def status(): CouchRequest[mapObject] = couch.makeGetRequest[mapObject](database)

  /**
   * Get the latest revision of an existing document from the database.
   *
   * @param id the id of the document
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def apply(id: String): CouchRequest[mapObject] =
    couch.makeGetRequest[mapObject](encode(id))

  /**
   * Get a particular revision of an existing document from the database.
   *
   * @param id the id of the document
   * @param rev the revision of the document
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def apply(id: String, rev: String): CouchRequest[mapObject] =
    couch.makeGetRequest[mapObject](encode(id, Seq("rev" -> rev)))

  /**
   * Get an existing document from the database.
   *
   * @param id the id of the document
   * @param properties the properties to add to the request
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def apply(id: String, properties: Map[String, String]): CouchRequest[JValue] =
    apply(id, properties.toSeq)

  /**
   * Get an existing document from the database.
   *
   * @param id the id of the document
   * @param properties the properties to add to the request
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def apply(id: String, properties: Seq[(String, String)]): CouchRequest[JValue] =
    couch.makeGetRequest[JValue](encode(id, properties))

  private[this] def query(id: String, properties: Seq[(String, String)]): CouchRequest[Result] = {
    couch.makeGetRequest[Result](encode(id, properties))
  }

  /**
   * Query a view from the database.
   *
   * @param design the design document
   * @param name the name of the view
   * @param properties the properties to add to the request
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def view(design: String, name: String, properties: Seq[(String, String)] = Seq()): CouchRequest[Result] =
    query("_design/" + design + "/_view/" + name, properties)

  /**
   * Call an update function.
   *
   * @param design the design document
   * @param name the name of the update function
   * @param data the data to pass to the update function
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def update(design: String, name: String, id: String, data: Map[String, String]): CouchRequest[JValue] = {
    val encoder = new QueryStringEncoder("")
    data foreach {
      case (key, value) => encoder.addParam(key, value)
    }
    couch.makePostRequest[JValue]("%s/_design/%s/_update/%s/%s".format(database, design, name, id), encoder.toString.tail)
  }

  /**
   * Retrieve the list of public documents from the database.
   *
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def allDocs(): CouchRequest[Result] = allDocs(Map())

  /**
   * Retrieve the list of public documents from the database.
   *
   * @param properties the properties to add to the request
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def allDocs(params: Map[String, String]): CouchRequest[Result] =
    query("_all_docs", params.toSeq)

  /**
   * Create the database.
   *
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def create(): CouchRequest[JValue] = couch.makePutRequest[JValue](database, None)

  /**
   * Compact the database.
   *
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def compact(): CouchRequest[JValue] =
    couch.makePostRequest[JValue](database + "/_compact", None)

  /**
   * Insert documents in bulk mode.
   *
   * @param docs the documents to insert
   * @param allOrNothing force an insertion of all documents
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def bulkDocs(docs: Seq[Any], allOrNothing: Boolean = false): CouchRequest[JValue] = {
    val args = Map("all_or_nothing" -> allOrNothing, "docs" -> docs)
    couch.makePostRequest[JValue](database + "/_bulk_docs", args)
  }

  private[this] def batchMode(query: String, batch: Boolean) =
    if (batch) query + "?batch=ok" else query

  /**
   * Insert a document into the database.
   *
   * @param doc the document to insert
   * @param id the id of the document if it is known and absent from the document itself
   * @param batch allow the insertion in batch (unchecked) mode
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def insert[T <% JObject](doc: T, id: String = null, batch: Boolean = false): CouchRequest[JValue] =
    if (id == null)
      couch.makePostRequest[JValue](batchMode(database, batch), Some(doc))
    else
      couch.makePutRequest[JValue](batchMode(database + "/" + id, batch), Some(doc))

  /**
   * Delete a document from the database.
   *
   * @param id the id of the document
   * @param rev the revision to delete
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def delete(id: String, rev: String): CouchRequest[JValue] =
    couch.makeDeleteRequest[JValue](database + "/" + id + "?rev=" + rev)

  /**
   * Delete a document from the database.
   *
   * @param doc the document which must contains an `_id` and a `_rev` field
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def delete[T <% JObject](doc: T): CouchRequest[JValue] = {
    val JString(id) = doc \ "_id"
    val JString(rev) = doc \ "_rev"
    delete(id, rev)
  }

  /**
   * Delete the database.
   *
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def delete(): CouchRequest[JValue] = couch.makeDeleteRequest[JValue](database)

  /**
   * Request the list of changes from the database.
   *
   * @param params the parameters to add to the request
   * @return a request
   *
   * @throws StatusCode if an error occurs
   *
   * @note The kind of request (continuous, longpoll, etc.) will determine the
   * result type.
   */
  def changes(params: Map[String, String] = Map()): CouchRequest[JValue] =
    couch.makeGetRequest[JValue](encode("_changes", params.toSeq), true)

  /**
   * Ensure that the database has been written to the permanent storage.
   *
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def ensureFullCommit(): CouchRequest[JValue] =
    couch.makePostRequest[JValue](database + "/_ensure_full_commit", None)

  /**
   * Launch a mono-directional replication from another database.
   *
   * @param source the database to replicate from
   * @param continuous true if the replication must be continuous, false otherwise
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def replicateFrom(source: Database, continuous: Boolean): CouchRequest[JObject] =
    couch.replicate(source, this, continuous)

  /**
   * Launch a mono-directional replication to another database.
   *
   * @param target the database to replicate to
   * @param continuous true if the replication must be continuous, false otherwise
   * @return a request
   *
   * @throws StatusCode if an error occurs
   */
  def replicateTo(target: Database, continuous: Boolean): CouchRequest[JObject] =
    couch.replicate(this, target, continuous)

}
