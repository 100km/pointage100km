package replicate.scrutineer

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.util.FastFuture
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import net.rfc1149.canape.Database
import play.api.libs.json.JsObject
import replicate.scrutineer.models.ContestantAnalysis
import replicate.state.RankingState
import replicate.state.RankingState.CheckpointData

import scala.concurrent.Future

class CheckpointScrutineer(database: Database) extends Actor with ActorLogging {

  import CheckpointScrutineer._

  private[this] implicit val materializer = ActorMaterializer()
  private[this] implicit val dispatcher = context.dispatcher

  private[this] val problemService = context.actorOf(Props(new ProblemService(database)), "problem-service")

  override def preStart() = {
    log.info("Starting the checkpoint scrutineer")
    // Load the initial values from the databases and rebuild the list of problems
    val sendInitialData =
      for (_ <- RankingState.reset();
           (lastSeq, checkpoints) <- database.viewWithUpdateSeq[Int, JsObject]("replicate", "checkpoint", Seq("include_docs" -> "true")))
        yield {
          checkpoints.groupBy(_._1).mapValues(_.map(_._2)).foreach { case (contestantId, docs) => self ! InitialData(contestantId, docs) }
          // The ReadyToStart message will only be received after all the problems above have been handled
          startChangesStream(lastSeq)
        }
    sendInitialData.recover {
      case throwable: Throwable =>
        log.error(throwable, "unable to initialize checkpoint scrutineer")
        self ! Suicide(throwable)
    }
  }

  def receive = {

    case Suicide(t) =>
      throw t

    case ReadyToStart =>
      log.info("Answering ready to start")
      sender ! Ack

    case InitialData(contestantId, docs) =>
      try {
        val allData = docs.map(parseCheckpointDoc)
        val zeroSites = allData.filter(_.raceId == 0).map(_.siteId).sorted.mkString(", ")
        if (zeroSites.nonEmpty) {
          log.warning("ignoring some initial checkpoint information for bib {} because race_id is unknown on site list {}: {}",
            contestantId, zeroSites, docs)
        }
        val data = allData.filterNot(_.raceId == 0)
        val problemFuture =
          for (_ <- Future.sequence(data.dropRight(1).map(RankingState.updateTimestamps));
               analysis <- data.headOption.fold(FastFuture.successful[Option[ContestantAnalysis]](None))(updateAndAnalyze(_).map(Some(_))))
            yield analysis.foreach(problemService ! _)
        problemFuture.recover {
          case t: Throwable =>
            log.error(t, "error during analysis of initial data for bib {}: {}", contestantId, docs)
        }
      } catch {
        case t: Throwable =>
          log.error(t, "unable to analyze initial data for bib {}: {}", contestantId, docs)
      }

    case Data(doc) =>
      sender ! Ack
      try {
        val data = parseCheckpointDoc(doc)
        if (data.raceId == 0)
          log.warning("ignoring checkpoint information for bib {} at site {} because race_id is unknown: {}",
            data.contestantId, data.siteId, doc)
        else
          updateAndAnalyze(data).map(problemService ! _).recover {
            case t: Throwable =>
              log.error(t, "error during analysis of bib {} at checkpoint {}: {}", data.contestantId, data.siteId)
          }
      } catch {
        case t: Throwable =>
          log.error(t, "unable to analyze document {}", doc)
      }

    case Failure(t) =>
      log.error(t, "Received a failure")
      throw t

    case other =>
      log.error(s"Received an unknown message: $other")
      throw new IllegalStateException

  }

  // Can throw if the checkpoint contains invalid data or is incomplete
  private[this] def parseCheckpointDoc(doc: JsObject): CheckpointData = {
    val contestantId = (doc \ "bib").as[Int]
    val raceId = (doc \ "race_id").as[Int]
    val siteId = (doc \ "site_id").as[Int]
    val timestamps = (doc \ "times").as[Seq[Long]]
    CheckpointData(contestantId, raceId, siteId, timestamps)
  }

  private[this] def updateAndAnalyze(checkpointData: CheckpointData): Future[ContestantAnalysis] =
    RankingState.updateTimestamps(checkpointData).map(rankInfo => Analyzer.analyze(checkpointData.contestantId, checkpointData.raceId, rankInfo))

  private[this] def startChangesStream(lastSeq: Long) =
    database.changesSource(Map("filter" -> "_view", "view" -> "replicate/checkpoint", "include_docs" -> "true"), sinceSeq = lastSeq)
        .map(js => Data((js \ "doc").as[JsObject]))
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
