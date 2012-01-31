package net.rfc1149.canape

import java.net.SocketAddress
import net.liftweb.json._
import org.jboss.netty.buffer._
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.util._

class JsonDecoder[T: Manifest] extends SimpleChannelUpstreamHandler {

  import implicits._

  private var readingChunks: Boolean = false

  private def sendUpstream(ctx: ChannelHandlerContext, data: Either[Throwable, T], remoteAddress: SocketAddress): Unit =
    ctx.sendUpstream(new UpstreamMessageEvent(ctx.getChannel, data, remoteAddress))

  private def sendUpstream(ctx: ChannelHandlerContext, data: ChannelBuffer, remoteAddress: SocketAddress): Unit = {
    val strData = data.toString(CharsetUtil.UTF_8)
    sendUpstream(ctx, Right(parse(strData).extract[T]), remoteAddress)
  }

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) =
    if (readingChunks) {
      val chunk = e.getMessage.asInstanceOf[HttpChunk]
      if (chunk.isLast)
	readingChunks = false
      else {
	val content = chunk.getContent
	if (content.readableBytes > 2)
	  sendUpstream(ctx, content, e.getRemoteAddress)
      }
    } else {
      val response = e.getMessage.asInstanceOf[HttpResponse]
      if (response.isChunked)
	readingChunks = true
      else
	sendUpstream(ctx, response.getContent, e.getRemoteAddress)
    }

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) =
    sendUpstream(ctx, Left(e.getCause), null)

}
