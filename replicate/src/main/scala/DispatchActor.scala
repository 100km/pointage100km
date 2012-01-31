import akka.actor.Actor
import akka.event.Logging

trait DispatchActor extends Actor {

  val log = Logging(context.system, this)

  val http = Global.makeHttp(log)

  override def postStop() = {
    http.shutdown()
    super.postStop()
  }

}
