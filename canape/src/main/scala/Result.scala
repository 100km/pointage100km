package net.rfc1149.canape

import dispatch._
import dispatch.liftjson.Js._
import net.liftweb.json._

class Result[K, V](js: JValue)(implicit val formats: Formats, implicit val k: Manifest[K], implicit val v: Manifest[V]) {

  val JInt(total_rows: BigInt) = js \ "total_rows"
  val JInt(offset: BigInt) = js \ "offset"
  val rows: List[Result.Row[K, V]] = (js \ "rows").children map { new Result.Row[K, V](_) }

}

object Result {

  class Row[K, V](js: JValue)(implicit val formats: Formats, implicit val k: Manifest[K], implicit val v: Manifest[V]) {

    val JString(id: String) = js \ "id"
    val key: K = (js \ "key").extract[K]
    val value: V = (js \ "value").extract[V]

  }

}
