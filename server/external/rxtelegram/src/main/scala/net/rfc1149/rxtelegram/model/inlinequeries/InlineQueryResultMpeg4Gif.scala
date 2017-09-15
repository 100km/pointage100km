package net.rfc1149.rxtelegram.model.inlinequeries

import net.rfc1149.rxtelegram.Bot.ParseMode
import play.api.libs.json.{ Json, Writes }

case class InlineQueryResultMpeg4Gif(id: String, mpeg4_url: String, mpeg4_width: Option[Long] = None, mpeg4_height: Option[Long] = None,
  thumb_url: String, title: Option[String] = None, caption: Option[String] = None,
  reply_markup: Option[InlineKeyboardMarkup] = None, input_message_content: Option[InputMessageContent] = None)

object InlineQueryResultMpeg4Gif {
  implicit val iqrmWrites: Writes[InlineQueryResultMpeg4Gif] = Writes { iqrm ⇒
    Json.writes[InlineQueryResultMpeg4Gif].writes(iqrm) ++ Json.obj("type" → "mpeg4_gif")
  }
}
