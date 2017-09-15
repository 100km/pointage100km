package net.rfc1149.rxtelegram.model.inlinequeries

import play.api.libs.json.{ Json, Writes }

case class InlineQueryResultPhoto(id: String, photo_url: String, photo_width: Option[Long] = None, photo_height: Option[Long] = None,
  thumb_url: Option[String] = None, title: Option[String] = None,
  description: Option[String] = None, caption: Option[String] = None,
  reply_markup: Option[InlineKeyboardMarkup] = None, input_message_content: Option[InputMessageContent] = None)

object InlineQueryResultPhoto {
  implicit val iqrpWrites: Writes[InlineQueryResultPhoto] = Writes { iqrp ⇒
    Json.writes[InlineQueryResultPhoto].writes(iqrp) ++ Json.obj("type" → "photo")
  }
}
