package net.rfc1149.rxtelegram.model

import play.api.libs.json.{ Json, Reads }

case class CallbackQuery(id: String, from: User, message: Option[Message], inline_message_id: Option[String], data: String)

object CallbackQuery {

  implicit val callbackQueryReads: Reads[CallbackQuery] = Json.reads[CallbackQuery]

}
