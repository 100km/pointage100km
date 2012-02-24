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

  private def encode(extra: String, properties: Seq[(String, String)] = Seq()) = {
    val encoder = new QueryStringEncoder(database + "/" + extra)
    properties foreach {
      case (name, value) => encoder.addParam(name, value)
    }
    encoder.toString
  }

  def status(): CouchRequest[mapObject] = couch.makeGetRequest[mapObject](database)

  def apply(id: String): CouchRequest[mapObject] =
    couch.makeGetRequest[mapObject](encode(id))

  def apply(id: String, rev: String): CouchRequest[mapObject] =
    couch.makeGetRequest[mapObject](encode(id, Seq("rev" -> rev)))

  def apply(id: String, properties: Map[String, String]): CouchRequest[JValue] =
    apply(id, properties.toSeq)

  def apply(id: String, properties: Seq[(String, String)]): CouchRequest[JValue] =
    couch.makeGetRequest[JValue](encode(id, properties))

  def query(id: String, properties: Seq[(String, String)]): CouchRequest[Result] = {
    couch.makeGetRequest[Result](encode(id, properties))
  }

  def view(design: String, name: String, properties: Seq[(String, String)] = Seq()): CouchRequest[Result] =
    query("_design/" + design + "/_view/" + name, properties)

  def update(design: String, name: String, id: String, data: Map[String, String]): CouchRequest[JValue] = {
    val encoder = new QueryStringEncoder("")
    data foreach {
      case (key, value) => encoder.addParam(key, value)
    }
    couch.makePostRequest[JValue]("%s/_design/%s/_update/%s/%s".format(database, design, name, id), encoder.toString.tail)
  }

  def allDocs(): CouchRequest[Result] = allDocs(Map())

  def allDocs(params: Map[String, String]): CouchRequest[Result] =
    query("_all_docs", params.toSeq)

  def create(): CouchRequest[JValue] = couch.makePutRequest[JValue](database, None)

  def compact(): CouchRequest[JValue] =
    couch.makePostRequest[JValue](database + "/_compact", None)

  def bulkDocs(docs: Seq[Any], allOrNothing: Boolean = false): CouchRequest[JValue] = {
    val args = Map("all_or_nothing" -> allOrNothing, "docs" -> docs)
    couch.makePostRequest[JValue](database + "/_bulk_docs", args)
  }

  private def batchMode(query: String, batch: Boolean) =
    if (batch) query + "?batch=ok" else query

  def insert[T <% JObject](doc: T, id: String = null, batch: Boolean = false): CouchRequest[JValue] =
    if (id == null)
      couch.makePostRequest[JValue](batchMode(database, batch), Some(doc))
    else
      couch.makePutRequest[JValue](batchMode(database + "/" + id, batch), Some(doc))

  def delete(id: String, rev: String): CouchRequest[JValue] =
    couch.makeDeleteRequest[JValue](database + "/" + id + "?rev=" + rev)

  def delete(): CouchRequest[JValue] = couch.makeDeleteRequest[JValue](database)

  def delete[T <% JObject](doc: T): CouchRequest[JValue] = {
    val JString(id) = doc \ "_id"
    val JString(rev) = doc \ "_rev"
    delete(id, rev)
  }

  def changes(params: Map[String, String] = Map()): CouchRequest[JValue] =
    couch.makeGetRequest[JValue](encode("_changes", params.toSeq), true)

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
  def replicateFrom(source: Database, continuous: Boolean): CouchRequest[JValue] =
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
  def replicateTo(target: Database, continuous: Boolean): CouchRequest[JValue] =
    couch.replicate(this, target, continuous)

}
