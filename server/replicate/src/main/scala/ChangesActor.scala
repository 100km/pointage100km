import akka.actor.{ActorRef, FSM}
import akka.event.Logging
import akka.stream.ActorFlowMaterializer
import akka.stream.actor.ActorSubscriberMessage.{OnError, OnNext}
import akka.stream.actor.{ActorSubscriber, WatermarkRequestStrategy}
import akka.stream.scaladsl.Sink
import net.liftweb.json._
import net.rfc1149.canape._

import scala.concurrent.duration._
import scala.language.postfixOps

class ChangesActor(sendTo: ActorRef, database: Database, filter: Option[String] = None) extends ActorSubscriber with FSM[ChangesActor.State, Unit] {

  implicit val formats = DefaultFormats
  implicit val system = context.system

  implicit val requestStrategy = WatermarkRequestStrategy(10)

  implicit var backoff: FiniteDuration = FiniteDuration(0, SECONDS)

  import ChangesActor._

  private[this] val log = Logging(context.system, this)

  private[this] implicit val materializer = ActorFlowMaterializer(None)

  private[this] def requestChanges() = database.continuousChanges(filter.map("filter" -> _).toMap).to(Sink(ActorSubscriber[JObject](self))).run()

  startWith(ChangesError, ())

  when(Processing) {
    case Event(OnNext(value), _) =>
      sendTo ! value
      backoff = FiniteDuration(0, SECONDS)
      log.info("resetting backoff")
      stay()
    case Event(OnError(t), _) =>
      log.warning("error when subscribing to changes stream: {}", t)
      goto(ChangesError)
  }

  when(ChangesError, stateTimeout = backoff) {
    case Event(StateTimeout, _) =>
      requestChanges()
      if (backoff < Global.maximumBackoffTime)
        backoff += Global.backoffTimeIncrement
      log.info("connecting, next backoff is {}", backoff)
      goto(Processing)
  }

  initialize()

}

object ChangesActor {
  sealed trait State

  private case object Processing extends State
  private case object ChangesError extends State
}
