package net.rfc1149.rxtelegram.model.inlinequeries

import net.rfc1149.rxtelegram.Bot.ParseMode
import play.api.libs.json.{Json, Writes}

case class InlineQueryResultArticle(id: String, title: String, message_text: String, parse_mode: Option[ParseMode] = None,
  disable_web_page_preview: Option[Boolean] = None, url: Option[String] = None, hide_url: Option[Boolean] = None,
  description: Option[String] = None, thumb_url: Option[String] = None,
  thumb_width: Option[Long] = None, thumb_height: Option[Long] = None) extends InlineQueryResult

object InlineQueryResultArticle {
  implicit val iqraWrites: Writes[InlineQueryResultArticle] = Writes { iqra ⇒
    Json.writes[InlineQueryResultArticle].writes(iqra) ++ Json.obj("type" → "article")
  }
}

