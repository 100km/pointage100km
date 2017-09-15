package net.rfc1149.rxtelegram.model

import play.api.libs.json.{ Json, Reads }

case class Video(file_id: String, width: Long, height: Long, duration: Long, thumb: Option[PhotoSize],
  mime_type: Option[String], file_size: Option[Long])

object Video {
  implicit val videoReads: Reads[Video] = Json.reads[Video]
}
