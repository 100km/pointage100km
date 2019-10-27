package net.rfc1149.rxtelegram.model.inlinequeries

import play.api.libs.json.{Json, Writes}

case class InlineQueryResultVenue(id: String, latitude: Float, longitude: Float,
    title: String, address: String, foursquare_id: Option[String] = None,
    reply_markup: Option[InlineKeyboardMarkup] = None, input_message_content: Option[InputMessageContent] = None,
    thumb_url: Option[String] = None, thumb_width: Option[Long] = None, thumb_height: Option[Long] = None)
  extends InlineQueryResult

object InlineQueryResultVenue {
  implicit val iqrvWrites: Writes[InlineQueryResultVenue] = Writes { iqrv =>
    Json.writes[InlineQueryResultVenue].writes(iqrv) ++ Json.obj("type" -> "venue")
  }
}
