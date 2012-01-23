import akka.actor.{Actor, ActorSystem, Props}
import akka.event.{Logging, LoggingAdapter}
import com.typesafe.config.ConfigFactory
import dispatch._

object Global {

  val config = Config("steenwerck.cfg", "../steenwerck.cfg")

  val system = ActorSystem("Replicator", ConfigFactory.load.getConfig("Replicator"))

  val pinnedProps = Props().withDispatcher("pinned-dispatcher")

  val log = Logging(system, "Replicate")

  def makeHttp(adapter: LoggingAdapter) =
    new Http {
      override def make_logger = new Logger {
	override def info(msg: String, items: Any*) = adapter.debug(msg.format(items: _*))
	override def warn(msg: String, items: Any*) = adapter.warning(msg.format(items: _*))
      }
    }

  def createActor(creator: => Actor, name: String) =
    system.actorOf(pinnedProps.withCreator(creator), name)

}
