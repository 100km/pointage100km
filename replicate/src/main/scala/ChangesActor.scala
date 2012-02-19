import akka.actor.{Actor, ActorRef, FSM, Status}
import akka.event.Logging
import akka.pattern._
import akka.util.duration._
import net.liftweb.json._
import net.rfc1149.canape._

import Global._

class ChangesActor(sendTo: ActorRef, database: Database, filter: Option[String] = None)
  extends Actor with FSM[ChangesActor.State, Long] {

  implicit val formats = DefaultFormats

  import ChangesActor._

  private[this] val log = Logging(context.system, this)

  private[this] def requestChanges(since: Long) = {
    val changes = database.changes(Map("feed" -> "continuous", "since" -> since.toString) ++
				   (if (filter.isDefined) Map("filter" -> filter.get) else Map()))
    changes.map(js => ((js \ "seq").extract[Long], js)).toStreamingFuture(self) pipeTo (self)
    goto(Connecting)
  }

  private[this] def getInitialSequence() = {
    database.status().toFuture().map(m => m("update_seq").extract[Long]).pipeTo(self)
    goto(InitialSequence) using (-1)
  }

  startWith(InitialSequence, -1)
  getInitialSequence()

  when(InitialSequence) {
    case Event(Status.Failure(e), _) =>
      goto(ChangesError) using (-1)
    case Event(latestSeq: Long, _) =>
      requestChanges(latestSeq) using (latestSeq)
  }

  when(Connecting) {
    case Event(Status.Failure(e), _) =>
      log.warning("cannot connect to the database: " + e)
      goto(ChangesError)
    case Event((), _) =>
      log.info("connected to the changes stream")
      goto(Processing)
  }

  when(ChangesError, stateTimeout = 5 seconds) {
    case Event(StateTimeout, latestSeq) =>
      if (latestSeq == -1)
        getInitialSequence()
      else
        requestChanges(latestSeq)
    case Event('closed, _) =>
      // This can happen after a 404 failure
  }

  when(Processing) {
    case Event(Status.Failure(e), _) =>
      log.warning("error while running change request: " + e)
      goto(ChangesError)
    case Event('closed, _) =>
      log.warning("stream closed while running change request")
      goto(ChangesError)
    case Event((latestSeq: Long, m: JObject), _) =>
      sendTo ! m
      stay() using (latestSeq)
  }

  initialize

}

object ChangesActor {

  sealed trait State

  private case object InitialSequence extends State

  private case object Connecting extends State

  private case object Processing extends State

  private case object ChangesError extends State

}
