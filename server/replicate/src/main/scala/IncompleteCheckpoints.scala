import akka.dispatch.{Future, Promise}
import akka.event.LoggingAdapter
import net.liftweb.json._
import net.rfc1149.canape._

import Global._

trait IncompleteCheckpoints {

  val log: LoggingAdapter

  import implicits._

  def fixIncompleteCheckpoints(db: Database) =
    for (r <- db.view("common", "incomplete-checkpoints").toFuture)
    yield Future.traverse(r.values[JObject]) {
      doc =>
        val JInt(bib) = doc \ "bib"
        db("contestant-" + bib).toFuture flatMap {
          r =>
            val JInt(race) = r("race")
            if (race != 0) {
              log.info("fixing incomplete race " + race + " for bib " + bib)
              db.insert(doc.replace("race_id" :: Nil, JInt(race))).toFuture recover {
                case e: Exception =>
                  log.warning("unable to fix contestant " + bib + ": " + e)
                  JNull
              }
            } else
              Promise.successful(JNull)
        } recover {
          case StatusCode(404, _) =>
            log.debug("no information available for contestant " + bib)
            JNull
        }
    }

}
