package net.rfc1149.rxtelegram.model

import play.api.libs.json.{ Json, Reads }

case class PhotoSize(file_id: String, width: Long, height: Long, file_size: Option[Long])

object PhotoSize {
  implicit val photoSizeReads: Reads[PhotoSize] = Json.reads[PhotoSize]
}
