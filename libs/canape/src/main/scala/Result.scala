package net.rfc1149.canape

import net.liftweb.json._

case class Result(val total_rows: Long,
		  val offset: Long,
		  val rows: List[Row]) {

  lazy val ids = rows map (_.id)
  def keys[K](implicit formats: Formats, k: Manifest[K]) = rows map (_.key.extract[K])
  def values[V](implicit formats: Formats, v: Manifest[V]) = rows map (_.value.extract[V])
  def items[K, V](implicit formats: Formats, k: Manifest[K], v: Manifest[V]) = rows map (_.extract[K, V](formats, k, v))

}

case class Row(val id: String, val key: JValue, val value: JValue) {

  def extract[K, V](implicit formats: Formats, k: Manifest[K], v: Manifest[V]): (String, K, V) =
    (id, key.extract[K], value.extract[V])

}
