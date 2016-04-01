package replicate.scrutineer

import akka.NotUsed
import akka.actor.{ActorLogging, ActorRef, Props}
import akka.actor.Status.Failure
import akka.http.scaladsl.util.FastFuture
import akka.pattern.pipe
import akka.stream.actor.{ActorSubscriber, ZeroRequestStrategy}
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnError, OnNext}
import akka.stream.scaladsl.Sink
import net.rfc1149.canape.Database
import play.api.libs.json.{JsObject, Json}
import replicate.scrutineer.Analyzer.ContestantAnalysis

/**
 * This class is in charge of keeping the analysis for every problematic concurrent
 * synchronized with the database.
 *
 * @param database the database in which the problems are stored
 */
class ProblemService(database: Database) extends ActorSubscriber with ActorLogging {

  import ProblemService._

  private[this] implicit val dispatcher = context.dispatcher

  override val requestStrategy = ZeroRequestStrategy

  // Keep a list of revisions for the current problems so that we can update them
  // in one round-trip.
  private[this] var knownProblemRevs = Map[Int, String]()

  override def preStart() = {
    val deleteFuture = database.view[String, String]("replicate", "problem").flatMap { problems ⇒
      if (problems.nonEmpty)
        database.bulkDocs(problems.map { case (id, rev) ⇒ deleteDocument(id, rev) })
      else
        FastFuture.successful(NotUsed)
    }
    deleteFuture.map(_ ⇒ Ready).pipeTo(self)
  }

  def receive = {

    case OnNext(analysis: ContestantAnalysis) ⇒
      val contestantId = analysis.contestantId
      val currentRev = knownProblemRevs.get(contestantId)
      // If we have to do a database operation, will wil suspend handling messages until this is done as to not confuse
      // ourselves about the version currently in the database. Also, this will prevent exceeding the max-connections
      // setting with multiple database connections at the same time.
      if (analysis.isOk)
        currentRev match {
          case Some(rev) ⇒
            knownProblemRevs -= analysis.contestantId
            database.delete(analysis.id, rev).map(_ ⇒ Ready).pipeTo(self)
          case None ⇒
            request(1)
        }
      else {
        val base: JsObject = ContestantAnalysis.contestantAnalysisWrites.writes(analysis).as[JsObject]
        val doc = currentRev.fold(base)(rev ⇒ base ++ Json.obj("_rev" → rev))
        log.info(s"Inserting problem for $contestantId")
        database.insert(doc).map(js ⇒ Written(contestantId, (js \ "rev").as[String])).pipeTo(self)
      }

    case OnComplete ⇒
      log.info("stream terminated")
      context.stop(self)

    case OnError(t) ⇒
      log.error(t, "stream failed with an error")
      throw t

    case Ready ⇒
      request(1)

    case Written(contestantId, rev) ⇒
      knownProblemRevs += contestantId → rev
      request(1)

    case Failure(t) ⇒
      log.error(t, "error during database access")
      request(1)
  }

}

object ProblemService {

  private case object Ready
  private case class Written(contestantId: Int, rev: String)

  private def deleteDocument(id: String, rev: String) = Json.obj("_id" → id, "_rev" → rev, "_deleted" → true)

  def problemServiceSink(database: Database): Sink[ContestantAnalysis, ActorRef] =
    Sink.actorSubscriber[ContestantAnalysis](Props(new ProblemService(database)))

}
