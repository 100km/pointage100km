package akka.http

import akka.actor.ActorRef
import org.jboss.netty.channel._

class Redirector(receiver: ActorRef) extends SimpleChannelUpstreamHandler {

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) =
    receiver ! e.getMessage

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) =
    receiver ! e.getCause

}
