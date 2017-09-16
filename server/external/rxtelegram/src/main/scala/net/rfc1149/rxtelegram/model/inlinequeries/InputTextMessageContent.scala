package net.rfc1149.rxtelegram.model.inlinequeries

import net.rfc1149.rxtelegram.Bot.ParseMode
import play.api.libs.json.{Json, Writes}

case class InputTextMessageContent(message_text: String, parse_mode: Option[ParseMode] = None,
    disable_web_page_preview: Option[Boolean] = None) extends InputMessageContent

object InputTextMessageContent {

  implicit val itmcWrites: Writes[InputTextMessageContent] = Json.writes[InputTextMessageContent]
}
