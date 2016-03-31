package replicate.scrutineer

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import net.rfc1149.canape.Database
import play.api.libs.json.JsObject
import replicate.state.CheckpointsState
import replicate.state.CheckpointsState.CheckpointData

import scala.concurrent.Future

class CheckpointScrutineer(database: Database) extends Actor with ActorLogging {

  import CheckpointScrutineer._

  private[this] implicit val materializer = ActorMaterializer()
  private[this] implicit val dispatcher = context.dispatcher

  private[this] val problemService = context.actorOf(Props(new ProblemService(database)), "problem-service")

  override def preStart() = {
    log.info("Loading existing data")
    // Load the initial values from the databases and rebuild the list of problems
    val sendInitialData =
      for (
        _ ← CheckpointsState.reset();
        (lastSeq, checkpoints) ← database.viewWithUpdateSeq[Int, JsObject]("replicate", "checkpoint", Seq("include_docs" → "true"))
      ) yield {
        checkpoints.groupBy(_._1).mapValues(_.map(_._2)).foreach { case (contestantId, docs) ⇒ self ! InitialData(contestantId, docs) }
        // The ReadyToStart message will only be received after all the problems above have been handled
        startChangesStream(lastSeq)
      }
    sendInitialData.recover {
      case throwable: Throwable ⇒
        log.error(throwable, "unable to initialize checkpoint scrutineer")
        self ! Suicide(throwable)
    }
  }

  def receive = {

    case Suicide(t) ⇒
      throw t

    case ReadyToStart ⇒
      log.info("Switching to live mode")
      sender ! Ack

    case InitialData(contestantId, docs) ⇒
      try {
        val allData = docs.map(parseCheckpointDoc)
        val zeroSites = allData.filter(_.raceId == 0).map(_.siteId).sorted.mkString(", ")
        if (zeroSites.nonEmpty) {
          log.warning(
            "ignoring some initial checkpoint information for bib {} because race_id is unknown on site list {}: {}",
            contestantId, zeroSites, docs
          )
        }
        val data = allData.filterNot(_.raceId == 0)
        Future.sequence(data.map(CheckpointsState.setTimes))
          .map(_.lastOption.foreach(points ⇒ problemService ! Analyzer.analyze(data.last.raceId, contestantId, points)))
          .onFailure {
            case t: Throwable ⇒
              log.error(t, "error during analysis of initial data for bib {}: {}", contestantId, docs)
          }
      } catch {
        case t: Throwable ⇒
          log.error(t, "unable to analyze initial data for bib {}: {}", contestantId, docs)
      }

    case Data(doc) ⇒
      sender ! Ack
      try {
        val data = parseCheckpointDoc(doc)
        if (data.raceId == 0)
          log.warning(
            "ignoring checkpoint information for bib {} at site {} because race_id is unknown: {}",
            data.contestantId, data.siteId, doc
          )
        else
          CheckpointsState.setTimes(data).map(points ⇒ problemService ! Analyzer.analyze(data.raceId, data.contestantId, points))
            .onFailure {
              case t: Throwable ⇒
                log.error(t, "error during analysis of bib {} at checkpoint {}: {}", data.contestantId, data.siteId)
            }
      } catch {
        case t: Throwable ⇒
          log.error(t, "unable to analyze document {}", doc)
      }

    case Failure(t) ⇒
      log.error(t, "Received a failure")
      throw t

    case other ⇒
      log.error(s"Received an unknown message: $other")
      throw new IllegalStateException

  }

  // Can throw if the checkpoint contains invalid data or is incomplete
  private[this] def parseCheckpointDoc(doc: JsObject): CheckpointData = {
    val raceId = (doc \ "race_id").as[Int]
    val contestantId = (doc \ "bib").as[Int]
    val siteId = (doc \ "site_id").as[Int]
    val timestamps = (doc \ "times").as[Seq[Long]]
    CheckpointData(raceId, contestantId, siteId, timestamps)
  }

  private[this] def startChangesStream(lastSeq: Long) =
    database.changesSource(Map("filter" → "_view", "view" → "replicate/checkpoint", "include_docs" → "true"), sinceSeq = lastSeq)
      .map(js ⇒ Data((js \ "doc").as[JsObject]))
      .runWith(Sink.actorRefWithAck(self, ReadyToStart, Ack, ChangesComplete))

}

object CheckpointScrutineer {

  private case object ReadyToStart
  private case object Ack
  private case object ChangesComplete

  private case class Suicide(throwable: Throwable)

  private case class InitialData(contestantId: Int, docs: Seq[JsObject])
  private case class Data(doc: JsObject)

}
