package replicate.state

import akka.{Done, NotUsed}
import akka.agent.Agent
import akka.event.LoggingAdapter
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import net.rfc1149.canape.Database
import play.api.libs.json.{JsError, JsSuccess}
import replicate.models.Contestant
import replicate.utils.Global

import scala.concurrent.Future

object ContestantState {

  import replicate.utils.Global.dispatcher

  private val contestantAgent = Agent(Map[Int, Contestant]())

  def startContestantAgent(database: Database)(implicit log: LoggingAdapter, fm: Materializer): Future[Done] =
    database.changesSource(Map("filter" → "replicate/contestant", "include_docs" → "true"), sinceSeq = 0)
      .mapAsyncUnordered(1) { js ⇒
        (js \ "doc").validate[Contestant] match {
          case JsSuccess(contestant, _) ⇒
            contestantAgent.alter(m ⇒ m + (contestant.contestantId → contestant))
          case JsError(_) ⇒
            log.error("unable to analyze document {}", (js \ "id").as[String])
            FastFuture.successful(NotUsed)
        }
      }
      .runWith(Sink.ignore)

  /**
   * Eventually consistent information about a contestant.
   *
   * @param contestantId the bib
   * @return the contestant if it exists
   */
  def contestantFromId(contestantId: Int): Option[Contestant] =
    contestantAgent().get(contestantId)

}
