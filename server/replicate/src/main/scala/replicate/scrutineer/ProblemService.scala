package replicate.scrutineer

import akka.NotUsed
import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, Stash}
import akka.http.scaladsl.util.FastFuture
import akka.pattern.pipe
import net.rfc1149.canape.Database
import play.api.libs.json.Json
import replicate.scrutineer.models.ContestantAnalysis

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
      // We will do a database operation, so suspend handling messages until this is done as to not confuse
      // ourselves about the version currently in the database.
      if (currentRev.isDefined)
        context.become(waitForReady)
      if (analysis.isOk)
        currentRev.foreach { rev ⇒
          knownProblemRevs -= analysis.contestantId
          database.delete(analysis.id, rev).map(_ ⇒ Ready).recover { case _ ⇒ Ready }.pipeTo(self)
        }
      else {
        val base = analysis.toJson
        val doc = currentRev.fold(base)(rev ⇒ base ++ Json.obj("_rev" → rev))
        database.insert(doc).map(js ⇒ Written(contestantId, (js \ "rev").as[String])).recover { case _ ⇒ Ready }.pipeTo(self)
      }
  }

}

object ProblemService {

  private case object Ready
  private case class Written(contestantId: Int, rev: String)

  private def deleteDocument(id: String, rev: String) = Json.obj("_id" → id, "_rev" → rev, "_deleted" → true)

  private def idForContestant(contestantId: Int): String = s"problem-$contestantId"

}
