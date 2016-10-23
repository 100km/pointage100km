package net.rfc1149.rxtelegram.model.inlinequeries

import play.api.libs.json.{Json, Writes}

case class InputVenueMessageContent(latitude: Float, longitude: Float, title: String, address: String,
  fousquare_id: Option[String] = None) extends InputMessageContent

object InputVenueMessageContent {
  implicit val ivmcWrites: Writes[InputVenueMessageContent] = Json.writes[InputVenueMessageContent]
}
