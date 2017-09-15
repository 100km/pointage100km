package net.rfc1149.rxtelegram.model.inlinequeries

import play.api.libs.json.{Json, Writes}

case class InlineKeyboardMarkup(inline_keyboard: Array[Array[InlineKeyboardButton]])

object InlineKeyboardMarkup {

  implicit val inlineKeyboardWrites: Writes[InlineKeyboardMarkup] = Json.writes[InlineKeyboardMarkup]
}
