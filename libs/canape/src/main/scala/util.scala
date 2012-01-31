package net.rfc1149.canape

import net.liftweb.json._
import net.liftweb.json.Extraction.decompose
import net.liftweb.json.Serialization.write
import org.jboss.netty.channel._

object util {

  def toJObject(doc: AnyRef): JObject = {
    implicit val formats = DefaultFormats
    doc match {
      case js: JValue => js.asInstanceOf[JObject]
      case _          => decompose(doc).asInstanceOf[JObject]
    }
  }

  def toJValue(something: Any): JValue = {
    implicit val formats = DefaultFormats
    something match {
	case js: JValue => js
	case _          => decompose(something)
    }
  }

  def toUpstreamHandler[T](f: T => Unit) =
    new SimpleChannelUpstreamHandler {

      override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) =
	f(e.getMessage.asInstanceOf[T])

      override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) =
	ctx.sendUpstream(e)
    }

}
