package net.rfc1149.rxtelegram.model.inlinequeries

import play.api.libs.json.{ Json, Writes }

case class InlineQueryResultLocation(id: String, latitude: String, longitude: String, title: String,
  reply_markup: Option[InlineKeyboardMarkup] = None, input_message_content: Option[InputMessageContent] = None,
  thumb_url: Option[String] = None, thumb_width: Option[String] = None, thumb_height: Option[String] = None)
  extends InlineQueryResult

object InlineQueryResultLocation {
  val iqrlWrites: Writes[InlineQueryResultLocation] = Writes { iqrl ⇒
    Json.writes[InlineQueryResultLocation].writes(iqrl) ++ Json.obj("type" → "location")
  }
}
