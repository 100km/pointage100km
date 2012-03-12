import akka.actor.{Actor, FSM}
import akka.event.Logging
import akka.util.duration._
import net.liftweb.json._
import net.rfc1149.canape._

class Watchdog(db: Database) extends Actor with FSM[Int, Unit] with LoggingError {

  override val log = Logging(context.system, this)

  startWith(0, ())

  when(0, stateTimeout = 30 seconds) {

    case Event(StateTimeout, _) =>
      if (Replicate.options.watchdog)
	withError(Replicate.ping(db), "cannot ping database")
      stay()

    case Event(js: JObject, _) =>
      stay()

  }

  initialize

}
