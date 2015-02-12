package net.rfc1149.canape

import akka.actor.{ActorRef, Status}
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration.Duration

trait CouchRequest[T <: AnyRef] {

  implicit val context: ExecutionContext

  implicit val m: Manifest[T]

  def connect(): ChannelFuture

  def send(channel: Channel, closeAfter: Boolean, lastHandler: ChannelUpstreamHandler)

  def toFuture(): Future[T] = {
    val result = Promise[T]()
    connect().addListener(new ChannelFutureListener {
      override def operationComplete(future: ChannelFuture) {
        if (future.isSuccess)
          send(future.getChannel, true, new CouchRequest.PromiseChannelUpstreamHandler(result))
        else
          result failure (future.getCause)
      }
    })
    result.future
  }

  def execute(atMost: Duration = Duration.Inf): T =
    Await.result(toFuture(), atMost)

  def toStreamingFuture(actor: ActorRef): Future[Unit] = {
    val result = Promise[Unit]()
    connect().addListener(new ChannelFutureListener {
      override def operationComplete(future: ChannelFuture) {
        if (future.isSuccess) {
          result.success(())
          send(future.getChannel, true, new CouchRequest.StreamingUpstreamHandler(actor))
        } else
          result.failure(future.getCause)
      }
    })
    result.future
  }

  def map[U <: AnyRef : Manifest](transformer: (T) => U): CouchRequest[U] =
    new TransformerRequest[T, U](this, transformer)

}

object CouchRequest {

  private class StreamingUpstreamHandler(actor: ActorRef)
    extends SimpleChannelUpstreamHandler {

    override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
      actor ! e.getMessage
    }

    override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
      actor ! Status.Failure(e.getCause)
    }

    override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
      actor ! 'closed
    }

  }

  class PromiseChannelUpstreamHandler[T: Manifest](promise: Promise[T])
    extends SimpleChannelUpstreamHandler {

    override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
      promise success (e.getMessage.asInstanceOf[T])
    }

    override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
      promise failure (e.getCause)
    }

  }

}

class SimpleCouchRequest[T <: AnyRef](bootstrap: HTTPBootstrap,
				      val request: HttpRequest,
				      allowChunks: Boolean)(implicit val m: Manifest[T],
							    val context: ExecutionContext)
  extends CouchRequest[T] {

  override def connect(): ChannelFuture = {
    val future = bootstrap.connect()
    val channel = future.getChannel
    if (!allowChunks)
      channel.getPipeline.addLast("aggregator", new ChunkAggregator(1024 * 1024))
    channel.getPipeline.addLast("requestInterceptor", new RequestInterceptor)
    channel.getPipeline.addLast("jsonDecoder", new JsonDecoder[T])
    future
  }

  override def send(channel: Channel, closeAfter: Boolean, lastHandler: ChannelUpstreamHandler) {
    if (closeAfter)
      request.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE)
    val pipeline = channel.getPipeline
    try {
      pipeline.remove("lastHandler")
    } catch {
      case _: java.util.NoSuchElementException =>
    }
    pipeline.addLast("lastHandler", lastHandler)
    channel.write(request)
  }

}

private class TransformerRequest[T <: AnyRef, U <: AnyRef](request: CouchRequest[T],
							   transformer: T => U)(implicit val m: Manifest[U])
  extends CouchRequest[U] {

  override implicit val context = request.context
  private implicit val n = request.m

  override def connect(): ChannelFuture = request.connect()

  override def send(channel: Channel, closeAfter: Boolean, lastHandler: ChannelUpstreamHandler) {
    request.send(channel, closeAfter, new HandlerTransformer(lastHandler, transformer))
  }

}
