package akka.http

import akka.actor.{Actor, ActorRef, ActorContext, Status}
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.handler.codec.http._

class HttpClient extends Actor {

  private[this] val bootstrap =
    new ClientBootstrap(new NioClientSocketChannelFactory(Executors.newCachedThreadPool,
							  Executors.newCachedThreadPool))

  bootstrap.setPipelineFactory(new ChannelPipelineFactory {
    override def getPipeline = {
      val pipeline = Channels.pipeline
      pipeline.addLast("codec", new HttpClientCodec)
      pipeline
    }
  })

  override def receive = {
      case Connect(host, port) => {
	val future = bootstrap.connect(new InetSocketAddress(host, port))
	future.addListener(new HttpClient.ConnectListener(sender, context))
      }
  }

  override def postStop() = bootstrap.releaseExternalResources()

}

object HttpClient {

  private class ConnectListener(actor: ActorRef, context: ActorContext) extends ChannelFutureListener {

    override def operationComplete(future: ChannelFuture) =
      actor ! (if (future.isSuccess)
		 new HttpConnection(future.getChannel, context)
               else
		 Status.Failure(future.getCause)
	       )

  }

}
