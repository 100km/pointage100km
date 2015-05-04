package replicate.utils

import akka.actor.{ActorLogging, ActorRef, FSM}
import akka.stream.ActorFlowMaterializer
import akka.stream.actor.ActorSubscriberMessage.{OnError, OnNext}
import akka.stream.actor.{ActorSubscriber, WatermarkRequestStrategy}
import akka.stream.scaladsl.Sink
import net.rfc1149.canape._
import play.api.libs.json.JsObject

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class ChangesActor(sendTo: ActorRef, database: Database, filter: Option[String] = None, params: Map[String, String] = Map())
  extends ActorSubscriber with ActorLogging with FSM[ChangesActor.State, Unit] {

  implicit val system = context.system

  implicit val dispatcher = system.dispatcher

  implicit val requestStrategy = WatermarkRequestStrategy(10)

  private[this] var backoff: FiniteDuration = FiniteDuration(0, SECONDS)

  import ChangesActor._

  private[this] implicit val materializer = ActorFlowMaterializer(None)

  private[this] var lastSeq: Option[Long] = None

  private[this] def requestChanges() = {
    for (since <- lastSeq match {
      case Some(ls) =>
        Future.successful(ls)
      case None =>
        database.status().map { status =>
          val ls = (status \ "update_seq").as[Long]
          lastSeq = Some(ls)
          ls
        }
    }) {
      val options = params ++ Map("since" -> since.toString, "heartbeat" -> Global.heartbeatInterval.toMillis.toString)
      database.continuousChanges(options ++ filter.map("filter" -> _).toMap).to(Sink(ActorSubscriber[JsObject](self))).run()
    }
  }

  startWith(ChangesError, ())

  when(Processing) {
    case Event(OnNext(value: JsObject), _) =>
      backoff = FiniteDuration(0, SECONDS)
      (value \ "seq").asOpt[Long] match {
        case Some(seq) =>
          sendTo ! value
          lastSeq = Some(seq)
        case None =>
          // We must have reached the end of the stream, we will receive OnComplete right after
      }
      stay()
    case Event(OnError(t), _) =>
      log.warning("error when subscribing to changes stream: {}", t)
      goto(ChangesError)
    case Event(onComplete, _) =>
      goto(ChangesError)
  }

  when(ChangesError, stateTimeout = backoff) {
    case Event(StateTimeout, _) =>
      requestChanges()
      if (backoff < Global.maximumBackoffTime)
        backoff += Global.backoffTimeIncrement
      goto(Processing)
  }

  initialize()

}

object ChangesActor {

  sealed trait State
  private case object Processing extends State
  private case object ChangesError extends State

}
