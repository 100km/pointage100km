package net.rfc1149.canape

import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.queue.BlockingReadHandler

trait CouchRequest[T] {

  def execute(): T

}

class SimpleCouchRequest[T: Manifest](bootstrap: HTTPBootstrap, val request: HttpRequest) extends CouchRequest[T] {

  private def connect(allowChunks: Boolean = false): ChannelFuture = {
    val future = bootstrap.connect()
    if (!allowChunks)
      future.getChannel.getPipeline.addLast("aggregator", new ChunkAggregator(1024*1024))
    future
  }

  override def execute(): T = {
    import implicits._
    val reader = new BlockingReadHandler[Either[Throwable, T]]
    val future = connect(false)
    future.await
    if (future.isSuccess) {
      request.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE)
      val channel = future.getChannel
      channel.getPipeline.addLast("requestInterceptor", new RequestInterceptor)
      channel.getPipeline.addLast("jsonDecoder", new JsonDecoder[T])
      channel.getPipeline.addLast("reader", reader)
      channel.write(request)
      reader.read().fold(throw _, { t: T => t })
    } else
      throw future.getCause
  }

}

class TransformerRequest[T, U](request: CouchRequest[T], transformer: T => U) extends CouchRequest[U] {

  override def execute(): U = transformer(request.execute)

}
