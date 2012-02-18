package net.rfc1149.canape

import java.util.concurrent.ArrayBlockingQueue
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._
import java.net.SocketAddress

trait CouchRequest[T] {

  type Choice = Either[Throwable, T]

  def connect(): ChannelFuture

  def send(channel: Channel, closeAfter: Boolean, lastHandler: ChannelUpstreamHandler)

  def send(channel: Channel, closeAfter: Boolean)(lastHandler: Choice => Unit) {
    send(channel, closeAfter, util.toUpstreamHandler(lastHandler))
  }

  def execute(): T = {
    val future = connect()
    if (future.await.isSuccess) {
      val reader = new ArrayBlockingQueue[Either[Throwable, T]](1)
      send(future.getChannel, true) {
        d => reader.add(d.asInstanceOf[Either[Throwable, T]])
      }
      reader.take().fold(throw _, {
        t: T => t
      })
    } else
      throw future.getCause
  }

}

class SimpleCouchRequest[T: Manifest](bootstrap: HTTPBootstrap, val request: HttpRequest, allowChunks: Boolean) extends CouchRequest[T] {

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

class TransformerRequest[T: Manifest, U <: AnyRef](request: CouchRequest[T], transformer: T => U) extends CouchRequest[U] {

  override def connect(): ChannelFuture = request.connect()

  override def send(channel: Channel, closeAfter: Boolean, lastHandler: ChannelUpstreamHandler) {
    send(channel, closeAfter, new HandlerTransformer(lastHandler, transformer))
  }

}
