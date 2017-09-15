package net.rfc1149.rxtelegram.model.inlinequeries

import play.api.libs.json.{ Json, Writes }

case class InputContactMessageContent(phone_number: String, first_name: String, last_name: Option[String] = None) extends InputMessageContent

object InputContactMessageContent {
  implicit val icmcWrites: Writes[InputContactMessageContent] = Json.writes[InputContactMessageContent]
}
