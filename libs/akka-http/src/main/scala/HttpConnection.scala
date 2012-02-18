package akka.http

import akka.actor.{ActorContext, ActorRef, Props}
import akka.dispatch.Future
import akka.pattern.ask
import java.util.NoSuchElementException
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._

class HttpConnection(channel: Channel, context: ActorContext) {

  private[this] val pipeline = channel.getPipeline
  pipeline.addLast("inflater", new HttpContentDecompressor)

  def close() {
    channel.close()
  }

  def send(request: HttpRequest)(implicit timeout: akka.util.Timeout): Future[HttpResponse] = {
    val receiver = context.actorOf(Props[DefaultReceiverActor])
    val future = ask(receiver, 'start)(timeout).mapTo[HttpResponse]
    sendAndReceive(request, receiver)
    future
  }

  def sendAndReceive(request: HttpRequest, receiver: ActorRef) {
    try {
      pipeline.remove("redirector")
    } catch {
      case _: NoSuchElementException =>
    }
    pipeline.addLast("redirector", new Redirector(receiver))
    channel.write(request)
  }

}
