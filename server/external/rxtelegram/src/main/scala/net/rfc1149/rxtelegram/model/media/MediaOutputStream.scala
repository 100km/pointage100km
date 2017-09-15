package net.rfc1149.rxtelegram.model.media

import java.io.{ ByteArrayOutputStream, OutputStream }

import akka.http.scaladsl.model.MediaType

case class MediaOutputStream(mediaType: MediaType.Binary, fileName: Option[String] = None) extends OutputStream with Media {
  private[this] val outputStream: ByteArrayOutputStream = new ByteArrayOutputStream()
  override def write(i: Int) = outputStream.write(i)

  override def toBodyPart(fieldName: String) = MediaData(mediaType, outputStream.toByteArray, fileName).toBodyPart(fieldName)
}
