package net.rfc1149.rxtelegram.model.media

import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.Multipart.FormData.BodyPart

case class MediaOnServer(file_id: String) extends Media {
  def toBodyPart(fieldName: String) = BodyPart(fieldName, HttpEntity(file_id))
}
