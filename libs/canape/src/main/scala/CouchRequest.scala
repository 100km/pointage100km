package net.rfc1149.canape

import akka.actor.{ActorRef, Status}
import akka.dispatch.{Await, ExecutionContext, Future, Promise}
import akka.util.Duration
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._

trait CouchRequest[T] {

  implicit val context: ExecutionContext

  implicit val m: Manifest[T]

  type Choice = Either[Throwable, T]

  def connect(): ChannelFuture

  def send(channel: Channel, closeAfter: Boolean, lastHandler: ChannelUpstreamHandler)

  def send(channel: Channel, closeAfter: Boolean)(lastHandler: Choice => Unit) {
    send(channel, closeAfter, util.toUpstreamHandler(lastHandler))
  }

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
    result
  }

  def execute(atMost: Duration = Duration.Inf): T =
    Await.result(toFuture(), atMost)

  def toStreamingFuture(actor: ActorRef): Future[Unit] = {
    val result = Promise[Unit]()
    connect().addListener(new ChannelFutureListener {
      override def operationComplete(future: ChannelFuture) {
        if (future.isSuccess) {
          result.success(())
          send(future.getChannel, true, new CouchRequest.ForwardChannelUpstreamHandler(actor))
        } else
          result.failure(future.getCause)
      }
    })
    result
  }

}

object CouchRequest {

  private class ForwardChannelUpstreamHandler(actor: ActorRef) extends SimpleChannelUpstreamHandler {

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

  private class PromiseChannelUpstreamHandler[T: Manifest](promise: Promise[T]) extends SimpleChannelUpstreamHandler {

    override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
      promise success (e.getMessage.asInstanceOf[T])
    }

    override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
      promise failure (e.getCause)
    }

  }

}

class SimpleCouchRequest[T](bootstrap: HTTPBootstrap,
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

class TransformerRequest[T, U <: AnyRef](request: CouchRequest[T],
                                         transformer: T => U)(implicit val m: Manifest[U])
  extends CouchRequest[U] {

  override implicit val context = request.context
  private implicit val n = request.m

  override def connect(): ChannelFuture = request.connect()

  override def send(channel: Channel, closeAfter: Boolean, lastHandler: ChannelUpstreamHandler) {
    request.send(channel, closeAfter, new HandlerTransformer(lastHandler, transformer))
  }

}
