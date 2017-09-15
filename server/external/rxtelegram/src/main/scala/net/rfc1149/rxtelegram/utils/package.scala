package net.rfc1149.rxtelegram

import net.rfc1149.rxtelegram.model.ReplyMarkup
import play.api.libs.json.Json

package object utils {

  implicit class ToField[T](value: T) {
    def toField(name: String): List[(String, String)] = value match {
      case Some(v) ⇒ v match {
        case markup: ReplyMarkup ⇒ List(name → Json.stringify(ReplyMarkup.replyMarkupWrites.writes(markup)))
        case _ ⇒ List(name → v.toString)
      }
      case v if v == None ⇒ Nil
      case v ⇒ List(name → v.toString)
    }
    def toField(name: String, default: T): List[(String, String)] =
      if (value != default) List(name → value.toString) else Nil
  }

}
