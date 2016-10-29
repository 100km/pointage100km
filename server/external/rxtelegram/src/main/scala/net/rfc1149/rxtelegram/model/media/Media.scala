package net.rfc1149.rxtelegram.model.media

import akka.http.scaladsl.model.Multipart.FormData.BodyPart

trait Media {
  def toBodyPart(fieldName: String): BodyPart
}