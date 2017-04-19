package replicate.maintenance

import akka.event.LoggingAdapter
import akka.http.scaladsl.util.FastFuture
import akka.{Done, NotUsed}
import net.rfc1149.canape.helpers._
import net.rfc1149.canape.{Couch, Database}
import play.api.libs.json._
import replicate.models.{CheckpointData, Contestant}
import replicate.utils.Global._
import replicate.utils.Types.RaceId

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait IncompleteCheckpoints {

  val log: LoggingAdapter

  def fixIncompleteCheckpoints(db: Database): Future[Done] =
    db.view[String, JsObject]("common", "incomplete-checkpoints").flatMap(Future.traverse(_) {
      case (rev, doc) ⇒
        val cpd = doc.as[CheckpointData]
        db(s"contestant-${cpd.contestantId}").map(_.as[Contestant]) flatMap {
          contestant ⇒
            if (contestant.raceId != RaceId(0)) {
              val newCpd = cpd.copy(raceId = contestant.raceId)
              val inserter = db.insert(newCpd.withIdRev(doc))
              inserter onComplete {
                case Success(_) ⇒
                  log.info("successfully fixed incomplete race information for {}", contestant)
                case Failure(t) ⇒
                  log.error(t, "unable to fix incomplete race information for {}", contestant)
              }
              inserter
            } else
              FastFuture.successful(NotUsed)
        } recover {
          case Couch.StatusError(404, _, _) ⇒
            log.info("no information available for contestant {}", cpd.contestantId)
        }
    }).map(_ ⇒ Done)

}
