package net.rfc1149.canape

import java.net.InetSocketAddress
import java.util.concurrent.Executors

import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.handler.codec.http._

trait NioHTTPBootstrap extends HTTPBootstrap {

  val host: String
  val port: Int

  protected[this] val bootstrap: ClientBootstrap =
    new ClientBootstrap(new NioClientSocketChannelFactory(Executors.newCachedThreadPool,
							  Executors.newCachedThreadPool))

  bootstrap.setPipelineFactory(new ChannelPipelineFactory {
    override def getPipeline = {
      val pipeline = Channels.pipeline()
      pipeline.addLast("codec", new HttpClientCodec)
      pipeline.addLast("inflater", new HttpContentDecompressor)
      pipeline
    }
  })

  override def connect(): ChannelFuture =
    bootstrap.connect(new InetSocketAddress(host, port))

  override def releaseExternalResources() {
    bootstrap.releaseExternalResources()
  }

}
