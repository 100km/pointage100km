package net.rfc1149.rxtelegram.model

import play.api.libs.json.{ Json, Reads }

case class Voice(file_id: String, duration: Long, mime_type: Option[String], file_size: Option[Long])

object Voice {
  implicit val voiceReads: Reads[Voice] = Json.reads[Voice]
}
