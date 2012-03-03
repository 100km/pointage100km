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

  private def sendUpstream(ctx: ChannelHandlerContext, data: ChannelBuffer, remoteAddress: SocketAddress) {
    val obj = parse(data.toString(CharsetUtil.UTF_8)).extract[T]
    ctx.sendUpstream(new UpstreamMessageEvent(ctx.getChannel, obj, remoteAddress))
  }

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
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
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
    ctx.sendUpstream(e)
  }

}
