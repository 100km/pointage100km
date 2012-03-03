import akka.actor.ActorSystem
import akka.event.Logging
import com.typesafe.config.ConfigFactory

object Global {

  val config = Config("steenwerck.cfg", "../steenwerck.cfg")

  val system = ActorSystem("Replicator", ConfigFactory.load.getConfig("Replicator"))

  implicit val dispatcher = system.dispatcher

  val log = Logging(system, "Replicate")

}
