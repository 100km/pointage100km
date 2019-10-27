package net.rfc1149.rxtelegram.model.inlinequeries

import play.api.libs.json.{Json, Writes}

case class InlineQueryResultVoice(id: String, voice_url: String, title: String, voice_duration: Option[Long] = None,
    reply_markup: Option[InlineKeyboardMarkup] = None, input_message_content: Option[InputMessageContent] = None)
  extends InlineQueryResult

object InlineQueryResultVoice {
  implicit val iqrvWrites: Writes[InlineQueryResultVoice] = Writes { iqrv =>
    Json.writes[InlineQueryResultVoice].writes(iqrv) ++ Json.obj("type" -> "voice")
  }
}
