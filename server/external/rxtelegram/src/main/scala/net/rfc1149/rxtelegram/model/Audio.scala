package net.rfc1149.rxtelegram.model

import play.api.libs.json.{Json, Reads}

case class Audio(file_id: String, duration: Long, performer: Option[String], title: Option[String],
    mime_type: Option[String], file_size: Option[Long])

object Audio {
  implicit val audioReads: Reads[Audio] = Json.reads[Audio]
}
