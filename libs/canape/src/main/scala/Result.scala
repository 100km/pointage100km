package net.rfc1149.canape

import net.liftweb.json._

case class Result(val total_rows: Long,
		  val offset: Long,
		  val rows: List[Row]) {

  lazy val ids = rows map (_.id)
  def keys[K](implicit formats: Formats, k: Manifest[K]) = rows map (_.key.extract[K])
  def values[V](implicit formats: Formats, v: Manifest[V]) = rows map (_.value.extract[V])
  def docsOption[D](implicit formats: Formats, d: Manifest[D]) = rows map (_.doc.map(_.extract[D]))
  def docs[D](implicit formats: Formats, d: Manifest[D]) = rows map (_.doc.get.extract[D])
  def items[K, V](implicit formats: Formats, k: Manifest[K], v: Manifest[V]) = rows map (_.extract[K, V](formats, k, v))
  def itemsWithDocOption[K, V, D](implicit formats: Formats, k: Manifest[K], v: Manifest[V], d: Manifest[D]) = rows map (_.extractWithDocOption[K, V, D])
  def itemsWithDoc[K, V, D](implicit formats: Formats, k: Manifest[K], v: Manifest[V], d: Manifest[D]) = rows map (_.extractWithDoc[K, V, D])

}

case class Row(val id: String, val key: JValue, val value: JValue, val doc: Option[JValue]) {

  def extract[K, V](implicit formats: Formats, k: Manifest[K], v: Manifest[V]): (String, K, V) =
    (id, key.extract[K], value.extract[V])

  def extractWithDocOption[K, V, D](implicit formats: Formats, k: Manifest[K], v: Manifest[V], d: Manifest[D]): (String, K, V, Option[D]) =
    (id, key.extract[K], value.extract[V], doc.map(_.extract[D]))

  def extractWithDoc[K, V, D](implicit formats: Formats, k: Manifest[K], v: Manifest[V], d: Manifest[D]): (String, K, V, D) =
    (id, key.extract[K], value.extract[V], doc.get.extract[D])

}
