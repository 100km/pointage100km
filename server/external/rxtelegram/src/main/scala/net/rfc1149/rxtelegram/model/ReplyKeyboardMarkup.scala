package net.rfc1149.rxtelegram.model

import play.api.libs.json.{Json, Writes}

case class ReplyKeyboardMarkup(keyboard: Array[Array[KeyboardButton]], resize_keyboard: Option[Boolean] = None,
    one_time_keyboard: Option[Boolean] = None, selective: Option[Boolean] = None) extends ReplyMarkup

object ReplyKeyboardMarkup {

  implicit val replyKeyboardMarkupWrites: Writes[ReplyKeyboardMarkup] = Json.writes[ReplyKeyboardMarkup]

}