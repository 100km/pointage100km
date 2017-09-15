package net.rfc1149.rxtelegram.model

import play.api.libs.json.{ Json, Reads }

case class Location(latitude: Double, longitude: Double)

object Location {
  implicit val locationReads: Reads[Location] = Json.reads[Location]
}
