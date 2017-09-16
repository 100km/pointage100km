package net.rfc1149.rxtelegram.model.inlinequeries

import play.api.libs.json.{Json, Writes}

case class InlineQueryResultAudio(id: String, audio_url: String, title: String, performer: Option[String] = None, audio_duration: Option[Long] = None,
    reply_markup: Option[InlineKeyboardMarkup] = None, input_message_content: Option[InputMessageContent] = None)
  extends InlineQueryResult

object InlineQueryResultAudio {
  val iqraWrites: Writes[InlineQueryResultAudio] = Writes { iqra ⇒
    Json.writes[InlineQueryResultAudio].writes(iqra) ++ Json.obj("type" → "audio")
  }
}
