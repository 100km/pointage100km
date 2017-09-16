package net.rfc1149.rxtelegram.model

import play.api.libs.json.{Json, Reads}

case class Chat(id: Long, `type`: String, title: Option[String], username: Option[String],
    first_name: Option[String], last_name: Option[String])

object Chat {
  implicit val chatReads: Reads[Chat] = Json.reads[Chat]
}
