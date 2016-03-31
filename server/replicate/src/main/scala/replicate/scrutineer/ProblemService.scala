package replicate.scrutineer

import akka.NotUsed
import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, Stash}
import akka.http.scaladsl.util.FastFuture
import akka.pattern.pipe
import net.rfc1149.canape.Database
import play.api.libs.json.{JsObject, Json}
import replicate.scrutineer.Analyzer.ContestantAnalysis

/**
 * This class is in charge of keeping the analysis for every problematic concurrent
 * synchronized with the database.
 *
 * @param database the database in which the problems are stored
 */
class ProblemService(database: Database) extends Actor with Stash with ActorLogging {

  import ProblemService._

  private[this] implicit val dispatcher = context.dispatcher

  // Keep a list of revisions for the current problems so that we can update them
  // in one round-trip.
  private[this] var knownProblemRevs = Map[Int, String]()

  override def preStart() = {
    context.become(waitForReady)
    val deleteFuture = database.view[String, String]("replicate", "problem").flatMap { problems ⇒
      if (problems.nonEmpty)
        database.bulkDocs(problems.map { case (id, rev) ⇒ deleteDocument(id, rev) })
      else
        FastFuture.successful(NotUsed)
    }
    deleteFuture.map(_ ⇒ Ready).recover { case _ ⇒ Ready }.pipeTo(self)
  }

  private[this] def waitForReady: Receive = {

    // Sent when the initial deletion has been terminated
    case Ready ⇒
      context.become(receive)
      unstashAll()

    // Send when the insertions for a particular contestant have terminated
    case Written(contestantId, rev) ⇒
      knownProblemRevs += contestantId → rev
      context.become(receive)
      unstashAll()

    case Failure(t) ⇒
      log.error(t, "error during initialization")
      throw t

    case msg ⇒
      stash()
  }

  def receive = {

    case analysis: ContestantAnalysis ⇒
      val contestantId = analysis.contestantId
      val currentRev = knownProblemRevs.get(contestantId)
      // If we have to do a database operation, will wil suspend handling messages until this is done as to not confuse
      // ourselves about the version currently in the database. Also, this will prevent exceeding the max-connections
      // setting with multiple database connections at the same time.
      if (analysis.isOk)
        currentRev.foreach { rev ⇒
          context.become(waitForReady)
          knownProblemRevs -= analysis.contestantId
          database.delete(analysis.id, rev).map(_ ⇒ Ready).recover { case _ ⇒ Ready }.pipeTo(self)
        }
      else {
        val base: JsObject = ContestantAnalysis.contestantAnalysisWrites.writes(analysis).as[JsObject]
        val doc = currentRev.fold(base)(rev ⇒ base ++ Json.obj("_rev" → rev))
        context.become(waitForReady)
        database.insert(doc).map(js ⇒ Written(contestantId, (js \ "rev").as[String])).recover {
          case r: Throwable ⇒
            log.error(r, s"unable to insert problem for contestant $contestantId in database")
            Ready
        }.pipeTo(self)
      }
  }

}

object ProblemService {

  private case object Ready
  private case class Written(contestantId: Int, rev: String)

  private def deleteDocument(id: String, rev: String) = Json.obj("_id" → id, "_rev" → rev, "_deleted" → true)

  private def idForContestant(contestantId: Int): String = s"problem-$contestantId"

}
