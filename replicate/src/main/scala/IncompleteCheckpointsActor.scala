import akka.util.duration._
import dispatch._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._

class IncompleteCheckpointsActor(db: Database) extends DispatchActor with PeriodicActor {

  protected val period = 5 seconds

  private def fixIncompleteCheckpoints() =
    for (doc <- http(db.view[Nothing, JValue]("bib_input", "incomplete-checkpoints")).values) {
      try {
	val JInt(bib) = doc \ "bib"
	val JInt(race) = http(db("contestant-" + bib)) \ "course"
	if (race != 0) {
	  log.info("fixing incomplete race " + race + " for bib " + bib)
	  http(db.insert(doc.replace("race_id" :: Nil, race)))
	}
      } catch {
	  case x: Exception =>
	    log.warning("unable to fix contestant: " + x)
      }
    }


  override def periodic() =
    try {
      fixIncompleteCheckpoints()
    } catch {
      case StatusCode(status, _) =>
	log.warning("unable to get incomplete checkpoints: " + status)
    }

}
