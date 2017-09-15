package net.rfc1149.rxtelegram.model.inlinequeries

import play.api.libs.json.{ Json, Writes }

case class InlineKeyboardButton(text: String, url: Option[String] = None, callback_data: Option[String] = None,
  switch_inline_query: Option[String] = None) {
  require(
    List(url, callback_data, switch_inline_query).flatten.size == 1,
    "exactly one of url, callback_data or switch_inline_query must be defined")
}

object InlineKeyboardButton {

  implicit val inlineKeyboardButtonWrites: Writes[InlineKeyboardButton] = Json.writes[InlineKeyboardButton]

}
