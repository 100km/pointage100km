package net.rfc1149.canape

import java.net.SocketAddress
import org.jboss.netty.channel._

class HandlerTransformer[T: Manifest, U <: AnyRef](handler: ChannelUpstreamHandler,
                                                   transformer: (T) => U)
  extends ChannelUpstreamHandler {

  override def handleUpstream(ctx: ChannelHandlerContext, e: ChannelEvent) {
    e match {
      case ev: MessageEvent =>
        handler.handleUpstream(ctx, new MessageEvent {
          override def getChannel: Channel = ev.getChannel

          override def getFuture: ChannelFuture = ev.getFuture

          override def getRemoteAddress: SocketAddress = ev.getRemoteAddress

          override def getMessage: AnyRef = transformer(ev.getMessage.asInstanceOf[T])
        })
      case other =>
        handler.handleUpstream(ctx, other)
    }
  }

}
