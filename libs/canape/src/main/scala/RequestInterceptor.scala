package net.rfc1149.canape

import java.net.SocketAddress
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._

class RequestInterceptor extends SimpleChannelUpstreamHandler {

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    e.getMessage match {
      case response: HttpResponse => {
        val status = response.getStatus
        val code = status.getCode
        if (code < 200 || code > 204)
          throw new StatusCode(code, status.getReasonPhrase)
        else
          ctx.sendUpstream(e)
      }
      case _ =>
        ctx.sendUpstream(e)
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
    ctx.sendUpstream(e)
  }

}
