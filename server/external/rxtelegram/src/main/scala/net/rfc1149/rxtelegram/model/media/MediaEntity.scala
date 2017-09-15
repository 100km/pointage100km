package net.rfc1149.rxtelegram.model.media

import akka.http.scaladsl.model.BodyPartEntity
import akka.http.scaladsl.model.Multipart.FormData.BodyPart

case class MediaEntity(entity: BodyPartEntity, fileName: Option[String] = None) extends Media {
  def toBodyPart(fieldName: String) = BodyPart(fieldName, entity,
    Map("filename" → fileName.getOrElse(s"media.${entity.contentType.mediaType.subType}")))
}
