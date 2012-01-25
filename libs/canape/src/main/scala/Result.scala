package net.rfc1149.canape

import net.liftweb.json._

import implicits._

case class Result(val total_rows: Long,
		  val offset: Long,
		  val rows: List[Row]) {

  lazy val ids = rows map (_.id)
  def keys[T: Manifest] = rows map (_.key.extract[T])
  def values[T: Manifest] = rows map (_.value.extract[T])
  def items[K: Manifest, V: Manifest] = rows map (_.extract[K, V])

}

case class Row(val id: String, val key: JValue, val value: JValue) {

  def extract[K: Manifest, V: Manifest]: (String, K, V) =
    (id, key.extract[K], value.extract[V])

}
