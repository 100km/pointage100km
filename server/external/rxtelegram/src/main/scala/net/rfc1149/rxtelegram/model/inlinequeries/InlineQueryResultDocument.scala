package net.rfc1149.rxtelegram.model.inlinequeries

import play.api.libs.json.{Json, Writes}

case class InlineQueryResultDocument(id: String, title: String, caption: Option[String] = None,
  document_url: String, mime_type: String, description: Option[String] = None,
  reply_markup: Option[InlineKeyboardMarkup] = None, input_message_content: Option[InputMessageContent] = None,
  thumb_url: Option[String] = None, thumb_width: Option[Long] = None, thumb_height: Option[Long] = None)
    extends InlineQueryResult

object InlineQueryResultDocument {
  val iqrdWrites: Writes[InlineQueryResultDocument] = Writes { iqrd ⇒
    Json.writes[InlineQueryResultDocument].writes(iqrd) ++ Json.obj("type" → "document")
  }
}
