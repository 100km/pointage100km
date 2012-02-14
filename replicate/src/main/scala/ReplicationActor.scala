import akka.actor.Actor
import akka.dispatch.Future
import akka.event.Logging
import akka.util.duration._
import net.rfc1149.canape._

import FutureUtils._
import Global._

class ReplicationActor(couch: Couch, local: Database, remote: Database) extends Actor {

  private val log = Logging(context.system, this)

  override def preStart() =
    self ! 'act

  override def receive = {
    case 'act =>
      Future.sequence(List(couch.replicate(local, remote, true).toFuture,
			   couch.replicate(remote, local, true).toFuture,
			   Replicate.ping(local))) onFailure {
			     case e: Exception =>
			       log.warning("cannot start replication: " + e)
			   } onComplete {
			     case _ => context.system.scheduler.scheduleOnce(5 seconds, self, 'act)
			   }
  }

}
