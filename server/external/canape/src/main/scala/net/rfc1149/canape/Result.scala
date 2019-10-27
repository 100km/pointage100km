package net.rfc1149.canape

import net.rfc1149.canape.Database.UpdateSequence
import play.api.libs.json._

case class Result(
    total_rows: Long,
    offset: Long,
    rows: List[Row],
    update_seq: Option[UpdateSequence]) {

  lazy val ids: List[String] = rows map (_.id)

  def keys[K: Reads]: Seq[K] = rows map (_.key.as[K])

  def values[V: Reads]: Seq[V] = rows map (_.value.as[V])

  def docs[D: Reads]: Seq[D] = rows flatMap (d => d.doc.toList.map(_.as[D]))

  def items[K: Reads, V: Reads]: Iterable[(String, K, V)] = rows map (r => (r.id, r.key.as[K], r.value.as[V]))

  def itemsWithDoc[K: Reads, V: Reads, D: Reads]: Iterable[(String, K, V, Option[D])] =
    rows map (r => (r.id, r.key.as[K], r.value.as[V], r.doc.map(_.as[D])))

}

object Result {

  private implicit val rowRead: Reads[Row] = Json.reads[Row]
  implicit val resultRead: Reads[Result] = Json.reads[Result]

}

case class Row(id: String, key: JsValue, value: JsValue, doc: Option[JsValue])
