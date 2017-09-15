package replicate.state

import akka.NotUsed
import akka.agent.Agent
import akka.event.LoggingAdapter
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import net.rfc1149.canape.Database
import play.api.libs.json.{ JsError, JsSuccess, JsValue }
import replicate.models.Contestant
import replicate.utils.Types.ContestantId

import scalaz.@@

object ContestantState {

  import replicate.utils.Global.dispatcher

  private val contestantAgent = Agent(Map[Int @@ ContestantId, Contestant]())

  def startContestantAgent(database: Database)(implicit log: LoggingAdapter, fm: Materializer): Unit =
    database.viewWithUpdateSeq[JsValue, Contestant]("common", "all_contestants").foreach {
      case (seq, rows) ⇒
        // Start with the initial contestants state
        contestantAgent.alter(_ ⇒ rows.map { case (_, contestant) ⇒ contestant.contestantId → contestant }.toMap)
        log.info("ContestantAgent: initial state loaded")
        // Then check for contestants changes
        database.changesSource(Map("filter" → "_view", "view" → "common/all_contestants", "include_docs" → "true"), sinceSeq = seq)
          .mapAsyncUnordered(1) { js ⇒
            (js \ "doc").validate[Contestant] match {
              case JsSuccess(contestant, _) ⇒
                contestantAgent.alter(m ⇒ m + (contestant.contestantId → contestant))
              case JsError(error) ⇒
                log.error("unable to analyze document {}: {}", (js \ "id").as[String], error)
                FastFuture.successful(NotUsed)
            }
          }
          .runWith(Sink.ignore)
    }

  /**
   * Eventually consistent information about a contestant.
   *
   * @param contestantId the bib
   * @return the contestant if it exists
   */
  def contestantFromId(contestantId: Int @@ ContestantId): Option[Contestant] =
    contestantAgent().get(contestantId)

}
