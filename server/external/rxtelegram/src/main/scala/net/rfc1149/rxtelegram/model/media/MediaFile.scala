package net.rfc1149.rxtelegram.model.media

import java.nio.file.Path

import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.model.{ContentType, MediaType}

case class MediaFile(mediaType: MediaType.WithFixedCharset, path: Path) extends Media {
  def toBodyPart(fieldName: String) = BodyPart.fromPath(fieldName, ContentType(mediaType), path)
}
