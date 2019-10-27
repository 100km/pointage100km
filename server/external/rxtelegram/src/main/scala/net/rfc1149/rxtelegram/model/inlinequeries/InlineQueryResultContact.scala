package net.rfc1149.rxtelegram.model.inlinequeries

import play.api.libs.json.{Json, Writes}

case class InlineQueryResultContact(id: String, phone_number: String, first_name: String, last_name: Option[String] = None,
    reply_markup: Option[InlineKeyboardMarkup] = None, input_message_content: Option[InputMessageContent] = None,
    thumb_url: Option[String] = None, thumb_width: Option[Long] = None, thumb_height: Option[Long] = None)
  extends InlineQueryResult

object InlineQueryResultContact {
  val iqrcWrites: Writes[InlineQueryResultContact] = Writes { iqrc =>
    Json.writes[InlineQueryResultContact].writes(iqrc) ++ Json.obj("type" -> "contact")
  }
}
