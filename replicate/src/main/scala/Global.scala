import akka.actor.{Actor, ActorSystem, Props}
import akka.event.{Logging, LoggingAdapter}
import com.typesafe.config.ConfigFactory

object Global {

  val config = Config("steenwerck.cfg", "../steenwerck.cfg")

  val system = ActorSystem("Replicator", ConfigFactory.load.getConfig("Replicator"))

  val pinnedProps = Props().withDispatcher("pinned-dispatcher")

  val log = Logging(system, "Replicate")

  def createActor(creator: => Actor, name: String) =
    system.actorOf(pinnedProps.withCreator(creator), name)

}
