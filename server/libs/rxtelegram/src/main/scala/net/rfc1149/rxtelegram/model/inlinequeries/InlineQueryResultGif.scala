package net.rfc1149.rxtelegram.model.inlinequeries

import net.rfc1149.rxtelegram.Bot.ParseMode
import play.api.libs.json.{Json, Writes}

case class InlineQueryResultGif(id: String, gif_url: String, gif_width: Option[Long] = None, gif_height: Option[Long] = None,
  thumb_url: String, title: Option[String] = None, caption: Option[String] = None,
  reply_markup: Option[InlineKeyboardMarkup] = None, input_message_content: Option[InputMessageContent] = None)

object InlineQueryResultGif {
  implicit val iqrgWrites: Writes[InlineQueryResultGif] = Writes { iqrg ⇒
    Json.writes[InlineQueryResultGif].writes(iqrg) ++ Json.obj("type" → "gif")
  }
}
