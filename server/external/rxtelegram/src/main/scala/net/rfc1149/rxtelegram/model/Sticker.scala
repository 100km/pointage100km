package net.rfc1149.rxtelegram.model

import play.api.libs.json.{Json, Reads}

case class Sticker(file_id: String, width: Long, height: Long, thumb: Option[PhotoSize], emoji: Option[String], file_size: Option[Long])

object Sticker {
  implicit val stickerReads: Reads[Sticker] = Json.reads[Sticker]
}
