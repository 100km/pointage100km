package net.rfc1149.rxtelegram.model

import play.api.libs.json.{Json, Writes}

case class KeyboardButton(text: String, request_contact: Option[Boolean] = None, request_location: Option[Boolean] = None)

object KeyboardButton {

  implicit val keyboardButtonWrites: Writes[KeyboardButton] = Json.writes[KeyboardButton]
}
