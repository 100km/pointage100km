package net.rfc1149.rxtelegram.model

import play.api.libs.json.{Json, Reads}

case class UserProfilePhotos(total_count: Long, photos: Array[Array[PhotoSize]])

object UserProfilePhotos {
  implicit val userProfilePhotosReads: Reads[UserProfilePhotos] = Json.reads[UserProfilePhotos]
}
