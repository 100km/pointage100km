package net.rfc1149.rxtelegram.model.inlinequeries

import net.rfc1149.rxtelegram.model.{Location, User}
import play.api.libs.json.{Json, Reads}

case class InlineQuery(id: String, from: User, location: Option[Location], query: String, offset: String)

object InlineQuery {
  implicit val inlineQueryReads: Reads[InlineQuery] = Json.reads[InlineQuery]
}