package replicate.scrutineer

import akka.actor.Status.Failure
import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.pipe
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnError, OnNext}
import akka.stream.actor.{ActorSubscriber, ZeroRequestStrategy}
import akka.stream.scaladsl.Sink
import net.rfc1149.canape.Database
import play.api.libs.json.{JsObject, Json}
import replicate.scrutineer.Analyzer.ContestantAnalysis

/**
 * This class is in charge of keeping the analysis for every concurrent
 * synchronized with the database.
 *
 * @param database the database in which the analyses are stored
 */
class AnalysisService(database: Database) extends ActorSubscriber with ActorLogging {

  import AnalysisService._

  private[this] implicit val dispatcher = context.dispatcher

  override val requestStrategy = ZeroRequestStrategy

  // Keep a list of revisions for the current analyses so that we can update them
  // in one round-trip.
  private[this] var knownAnalysesRevs = Map[Int, String]()

  override def preStart() = {
    database.view[String, String]("replicate", "analysis").map { analyses ⇒
      InitialRevs(for ((id, rev) ← analyses) yield (id.stripPrefix("analysis-").toInt, rev))
    } pipeTo self
  }

  def receive = {

    case OnNext(analysis: ContestantAnalysis) ⇒
      val contestantId = analysis.contestantId
      val currentRev = knownAnalysesRevs.get(contestantId)
      val base: JsObject = ContestantAnalysis.contestantAnalysisWrites.writes(analysis).as[JsObject]
      val doc = currentRev.fold(base)(rev ⇒ base ++ Json.obj("_rev" → rev))
      database.insert(doc).map(js ⇒ Written(contestantId, (js \ "rev").as[String])).pipeTo(self)

    case OnComplete ⇒
      log.info("stream terminated")
      context.stop(self)

    case OnError(t) ⇒
      log.error(t, "stream failed with an error")
      throw t

    case InitialRevs(revs) ⇒
      knownAnalysesRevs = revs.toMap
      request(1)

    case Ready ⇒
      request(1)

    case Written(contestantId, rev) ⇒
      knownAnalysesRevs += contestantId → rev
      request(1)

    case Failure(t) ⇒
      log.error(t, "error during database access")
      request(1)
  }

}

object AnalysisService {

  private case object Ready
  private case class Written(contestantId: Int, rev: String)
  private case class InitialRevs(revs: Seq[(Int, String)])

  private def deleteDocument(id: String, rev: String) = Json.obj("_id" → id, "_rev" → rev, "_deleted" → true)

  def analysisServiceSink(database: Database): Sink[ContestantAnalysis, ActorRef] =
    Sink.actorSubscriber[ContestantAnalysis](Props(new AnalysisService(database)))

}
