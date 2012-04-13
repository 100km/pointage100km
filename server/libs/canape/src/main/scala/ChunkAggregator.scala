package net.rfc1149.canape

import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._

class ChunkAggregator(capacity: Int) extends HttpChunkAggregator(capacity) {

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
    ctx.sendUpstream(e)
  }

}

