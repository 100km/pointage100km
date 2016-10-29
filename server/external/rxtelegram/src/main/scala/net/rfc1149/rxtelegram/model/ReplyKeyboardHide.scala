package net.rfc1149.rxtelegram.model

import play.api.libs.json.{Json, Writes}

case class ReplyKeyboardHide(hide_keyboard: Boolean = true, selective: Option[Boolean] = None) extends ReplyMarkup

object ReplyKeyboardHide {
  implicit val replyKeyboardHideWrites: Writes[ReplyKeyboardHide] = Json.writes[ReplyKeyboardHide]
}
