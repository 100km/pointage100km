package net.rfc1149.rxtelegram.model

import play.api.libs.json.{ Json, Reads }

case class MessageEntity(`type`: String, offset: Long, length: Long, url: Option[String])

object MessageEntity {
  implicit val messageEntityReads: Reads[MessageEntity] = Json.reads[MessageEntity]
}
