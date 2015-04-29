package replicate.maintenance

import akka.actor.{Actor, FSM}
import akka.event.Logging
import net.rfc1149.canape._
import play.api.libs.json.JsObject
import replicate.Replicate
import replicate.utils.{LoggingError, Options}

import scala.concurrent.duration._
import scala.language.postfixOps

class Watchdog(options: Options.Config, db: Database) extends Actor with FSM[Int, Unit] with LoggingError {

  override val log = Logging(context.system, this)

  startWith(0, ())

  when(0, stateTimeout = 30 seconds) {

    case Event(StateTimeout, _) =>
      if (Replicate.options.ping)
        withError(steenwerck.ping(db, options.siteId), "cannot ping database")
      stay()

    case Event(js: JsObject, _) =>
      stay()

  }

  initialize()

}
