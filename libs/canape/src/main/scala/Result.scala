package net.rfc1149.canape

import dispatch._
import dispatch.liftjson.Js._
import net.liftweb.json._

final class Result[K: Manifest, V: Manifest](js: JValue,
					     formats: Formats) {

  val JInt(total_rows: BigInt) = js \ "total_rows"
  val JInt(offset: BigInt) = js \ "offset"
  val rows: List[Result.Row[K, V]] = (js \ "rows").children map { new Result.Row[K, V](_, formats) }

  lazy val ids = rows map (_.id)
  lazy val keys = rows map (_.key)
  lazy val values = rows map (_.value)

}

object Result {

  final class Row[K: Manifest, V: Manifest](js: JValue,
					    formats: Formats) {

    private[this] implicit val f = formats

    val JString(id: String) = js \ "id"
    val key: K = (js \ "key").extract[K]
    val value: V = (js \ "value").extract[V]

  }

}
