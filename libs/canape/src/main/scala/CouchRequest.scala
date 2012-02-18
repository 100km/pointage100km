package net.rfc1149.canape

import java.util.concurrent.ArrayBlockingQueue
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._

trait CouchRequest[T] {

  type Choice = Either[Throwable, T]

  def connect(): ChannelFuture

  def send(channel: Channel, closeAfter: Boolean)(lastHandler: Choice => Unit)

  def execute(): T = {
    val future = connect()
    if (future.await.isSuccess) {
      val reader = new ArrayBlockingQueue[Either[Throwable, T]](1)
      send(future.getChannel, true) { d => reader.add(d.asInstanceOf[Either[Throwable, T]]) }
      reader.take().fold(throw _, { t: T => t })
    } else
      throw future.getCause
  }

}

class SimpleCouchRequest[T: Manifest](bootstrap: HTTPBootstrap, val request: HttpRequest, allowChunks: Boolean) extends CouchRequest[T] {

  override def connect(): ChannelFuture = {
    val future = bootstrap.connect()
    val channel = future.getChannel
    if (!allowChunks)
      channel.getPipeline.addLast("aggregator", new ChunkAggregator(1024*1024))
    channel.getPipeline.addLast("requestInterceptor", new RequestInterceptor)
    channel.getPipeline.addLast("jsonDecoder", new JsonDecoder[T])
    future
  }

  def send(channel: Channel, closeAfter: Boolean)(lastHandler: Choice => Unit) {
    if (closeAfter)
      request.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE)
    val pipeline = channel.getPipeline
    try {
      pipeline.remove("lastHandler")
    } catch {
      case _: java.util.NoSuchElementException =>
    }
    pipeline.addLast("lastHandler", util.toUpstreamHandler(lastHandler))
    channel.write(request)
  }

}

class TransformerRequest[T: Manifest, U](request: CouchRequest[T], transformer: T => U) extends CouchRequest[U] {

  override def connect(): ChannelFuture = request.connect()

  override def send(channel: Channel, closeAfter: Boolean)(lastHandler: Choice => Unit) {
    request.send(channel, closeAfter) {
      d: Either[Throwable, T] =>
        lastHandler(d.fold({
          t: Throwable => Left(t)
        }, {
          t: T => Right(transformer(t))
        }))
    }
  }
}
