package net.rfc1149.rxtelegram.model

import play.api.libs.json.{ Json, Reads }

case class Venue(location: Location, title: String, address: String, foursquare_id: Option[String] = None)

object Venue {
  implicit val venueReads: Reads[Venue] = Json.reads[Venue]
}
