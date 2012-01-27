package net.rfc1149.canape

import net.liftweb.json._
import net.liftweb.json.Serialization.write

object util {

  def toJObject(doc: AnyRef): JObject = {
    implicit val formats = DefaultFormats
    doc match {
      case js: JValue => js.asInstanceOf[JObject]
      case other      => parse(write(other)).extract[JObject]
    }
  }

}
