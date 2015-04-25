import Global._
import akka.event.LoggingAdapter
import net.rfc1149.canape._
import play.api.libs.json._

import scala.concurrent.Future

trait IncompleteCheckpoints {

  val log: LoggingAdapter

  def fixIncompleteCheckpoints(db: Database): Future[Unit] =
    db.view[JsValue, JsObject]("common", "incomplete-checkpoints").flatMap(Future.traverse(_) { case (_, doc) =>
      val bib = (doc \ "bib").as[Int]
      db("contestant-" + bib) flatMap {
        r =>
          val race = (r \ "race").as[Int]
          if (race != 0) {
            val first = (r \ "first_name").as[String]
            val name = (r \ "name").as[String]
            val contestant = s"contestant $bib ($first $name) in race $race"
            val newDoc = doc.transform((__ \ 'race_id).json.update(__.read(JsNumber(race)))).get
            val inserter = db.insert(newDoc)
            inserter onSuccess {
              case _ =>
                log.info(s"successfully fixed incomplete race information for $contestant")
            }
            inserter recover {
              case e: Exception =>
                log.warning(s"unable to fix incomplete race information for $contestant: $e")
                JsUndefined
            }
          } else
            Future.successful(JsUndefined)
      } recover {
        case Couch.StatusError(404, _, _) =>
          log.debug("no information available for contestant " + bib)
          JsUndefined
      }
    }).map(_ => ())

}
