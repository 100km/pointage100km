package net.rfc1149.canape

import dispatch._
import dispatch.liftjson.Js._
import net.liftweb.json._

class Query[K, V](val db: Database,
		  val query: Request)(implicit val k: Manifest[K],
				      implicit val v: Manifest[V],
				      implicit val formats: Formats) {

  def apply(params: Map[String, String] = Map()) = query <<? params ># (new Result[K, V](_))

}
