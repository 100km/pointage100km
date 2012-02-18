import akka.actor.{ActorRef, Status}
import akka.dispatch.{ExecutionContext, Future, Promise}
import net.rfc1149.canape.CouchRequest
import org.jboss.netty.channel.{ChannelFuture, ChannelFutureListener}

object FutureUtils {

  implicit def toAkka[T](request: CouchRequest[T]) =
    new {
      def toFuture()(implicit context: ExecutionContext): Future[T] = {
        val result = Promise[T]()
        request.connect().addListener(new ChannelFutureListener {
          override def operationComplete(future: ChannelFuture) {
            if (future.isSuccess)
              request.send(future.getChannel, true) {
                d => result.complete(d.asInstanceOf[Either[Throwable, T]])
              }
            else
              result.complete(Left(future.getCause))
          }
        })
        result
      }

      def toStreamingFuture(actor: ActorRef)(implicit context: ExecutionContext): Future[Boolean] = {
        val result = Promise[Boolean]()
        request.connect().addListener(new ChannelFutureListener {
          override def operationComplete(future: ChannelFuture) {
            if (future.isSuccess) {
              result.success(true)
              request.send(future.getChannel, true) {
                d => d.asInstanceOf[Either[Throwable, T]] match {
                  case Right(t) => actor ! t
                }
              }
            } else
              result.failure(future.getCause)
          }
        })
        result
      }
    }

}
