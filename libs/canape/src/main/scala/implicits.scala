package net.rfc1149.canape

import net.liftweb.json._
import org.jboss.netty.channel._

object implicits {

  implicit val formats: Formats = DefaultFormats

  implicit def toRichJValue(js: JValue): RichJValue = new RichJValue(js)

  class RichJValue(js: JValue) {
    def childrenAs[T: Manifest]: Seq[T] = js.children.map(_.asInstanceOf[T])
    lazy val toMap: mapObject = js.extract[mapObject]
    def subSeq[T: Manifest](field: String): Seq[T] = (js \ field).children.map(_.extract[T])
  }

}
