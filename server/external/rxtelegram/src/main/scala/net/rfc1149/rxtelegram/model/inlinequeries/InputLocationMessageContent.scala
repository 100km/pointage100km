package net.rfc1149.rxtelegram.model.inlinequeries

import play.api.libs.json.{ Json, Writes }

case class InputLocationMessageContent(latitude: Float, longitude: Float) extends InputMessageContent

object InputLocationMessageContent {
  implicit val ilmcWrites: Writes[InputLocationMessageContent] = Json.writes[InputLocationMessageContent]
}
