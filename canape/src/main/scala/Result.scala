package net.rfc1149.canape

import dispatch._
import dispatch.liftjson.Js._
import net.liftweb.json._

class Result[K, V](js: JValue)(implicit val formats: Formats, implicit val k: Manifest[K], implicit val v: Manifest[V]) {

  val JInt(total_rows) = js \ "total_rows"
  val JInt(offset) = js \ "offset"
  val rows = (js \ "rows").children map { new Row[K, V](_) }

}

class Row[K, V](js: JValue)(implicit val formats: Formats, implicit val k: Manifest[K], implicit val v: Manifest[V]) {

  val JString(id) = js \ "id"
  val key = (js \ "key").extract[K]
  val value = (js \ "value").extract[V]

}

class Query[K, V](val db: Db, val query: Request)(implicit val k: Manifest[K], implicit val v: Manifest[V], implicit val formats: Formats) {

  def apply(params: Map[String, String] = Map()) = query <<? params ># (new Result[K, V](_))

}

class View[K, V](db: Db, val design: String, val viewName: String)(implicit val ik: Manifest[K], implicit val iv: Manifest[V], implicit val iformats: Formats) extends Query[K, V](db, db / "_design" / design / "_view" / viewName)(ik, iv, iformats)
