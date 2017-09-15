package net.rfc1149.rxtelegram.model

import play.api.libs.json.{ Json, Reads }

case class File(file_id: String, file_size: Option[Long], file_path: Option[String])

object File {
  implicit val fileReads: Reads[File] = Json.reads[File]
}
