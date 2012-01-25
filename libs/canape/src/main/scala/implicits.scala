package net.rfc1149.canape

import net.liftweb.json._
import org.jboss.netty.channel._

object implicits {

  implicit val formats: Formats = DefaultFormats

  implicit def toScalaPipeline(pipeline: ChannelPipeline): ScalaPipeline =
    new ScalaPipeline(pipeline)

  implicit def toScalaPipeline(channel: Channel): ScalaPipeline =
    new ScalaPipeline(channel.getPipeline)

  implicit def toScalaFuture(future: ChannelFuture): ScalaFuture =
    new ScalaFuture(future)

  implicit def toRichJValue(js: JValue): RichJValue = new RichJValue(js)

  class RichJValue(js: JValue) {
    def childrenAs[T: Manifest]: Seq[T] = js.children.map(_.asInstanceOf[T])
    lazy val toMap: Map[String, JValue] = js.extract[Map[String, JValue]]
    def subSeq[T: Manifest](field: String): Seq[T] = (js \ field).children.map(_.extract[T])
  }

  class ScalaPipeline(pipeline: ChannelPipeline) {

    def addLast[T: Manifest](name: String)(action: T => Unit): Unit =
      pipeline.addLast(name, new SimpleChannelUpstreamHandler {
	override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
	  action(e.getMessage.asInstanceOf[T])
	}
      })

    def onException(action: (ChannelHandlerContext, Throwable) => Unit): Unit =
      pipeline.addLast("onException", new SimpleChannelUpstreamHandler {
	override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
	  action(ctx, e.getCause)
	}
      })

    def onClose(action: Channel => Unit): Unit =
      pipeline.addLast("onClose", new SimpleChannelUpstreamHandler {
	override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
	  action(ctx.getChannel)
	}
      })

  }

  class ScalaFuture(future: ChannelFuture) {

    def onConnected(action: ChannelFuture => Unit): Unit =
      future.addListener(new ChannelFutureListener {
	override def operationComplete(future: ChannelFuture) {
	  action(future)
	}
      })

  }

}
