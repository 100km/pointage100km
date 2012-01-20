import akka.actor.Actor
import akka.event.Logging
import dispatch._

trait DispatchActor extends Actor {

  val log = Logging(context.system, this)

  val http = Replicate.makeHttp(log)

  override def postStop() = {
    http.shutdown()
    super.postStop()
  }

}
