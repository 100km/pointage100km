package net.rfc1149.rxtelegram.model.inlinequeries

import play.api.libs.json.{Json, Writes}

case class InlineQueryResultVideo(id: String, video_url: String, mime_type: String,
    video_width: Option[Long] = None, video_height: Option[Long] = None,
    video_duration: Option[Long] = None, thumb_url: Option[String] = None,
    title: Option[String] = None,
    description: Option[String] = None,
    reply_markup: Option[InlineKeyboardMarkup] = None, input_message_content: Option[InputMessageContent] = None)

object InlineQueryResultVideo {
  implicit val iqrvWrites: Writes[InlineQueryResultVideo] = Writes { iqrv ⇒
    Json.writes[InlineQueryResultVideo].writes(iqrv) ++ Json.obj("type" → "video")
  }
}
