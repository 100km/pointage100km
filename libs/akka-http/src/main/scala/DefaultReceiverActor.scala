package akka.http

import akka.actor.{Actor, ActorRef, FSM, Status}
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.buffer._

class DefaultReceiverActor extends Actor with FSM[DefaultReceiverActor.State,
						  DefaultReceiverActor.Data] {

  import DefaultReceiverActor._
  import FSM._

  startWith(WaitingForActorRef, new Data)

  when(WaitingForActorRef) {
    case Event('start, data) =>
      goto(WaitingForAnswer) using (data.withReplyTo(sender))
  }

  when(WaitingForAnswer) {
    case Event(r: HttpResponse, data) =>
      if (r.isChunked)
	goto(WaitingForChunk) using (data.withResponse(r))
      else {
	data.replyTo.get ! r
	stop(Normal)
      }
  }

  when (WaitingForChunk) {
    case Event(c: HttpChunk, data) =>
      if (c.isLast) {
	data.response.removeHeader("transfer-encoding")
	data.response.setChunked(false)
	data.response.setContent(data.buffer)
	data.replyTo.get ! data.response
	stop(Normal)
      } else {
	stay using (data.withMoreData(c.getContent))
      }
  }

  whenUnhandled {
    case Event(t: Throwable, data) =>
      data.replyTo.foreach (_ ! Status.Failure(t))
      stop(Normal)
  }

  initialize

}

object DefaultReceiverActor {

  sealed trait State

  case object WaitingForActorRef extends State
  case object WaitingForAnswer extends State
  case object WaitingForChunk extends State

  class Data {
    var replyTo: Option[ActorRef] = None
    var buffer: ChannelBuffer = _
    var response: HttpResponse = _

    def withReplyTo(r: ActorRef) = {
      replyTo = Some(r)
      this
    }

    def withMoreData(b: ChannelBuffer) = {
      if (buffer == null)
	buffer = ChannelBuffers.dynamicBuffer(b.readableBytes)
      b.readBytes(buffer)
      this
    }

    def withResponse(r: HttpResponse) = {
      response = r
      this
    }
  }

}
