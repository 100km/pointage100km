import akka.event.Logging
import akka.util.duration._
import net.liftweb.json._
import net.rfc1149.canape._

class IncompleteCheckpointsActor(db: Database) extends PeriodicActor {

  import implicits._

  private val log = Logging(context.system, this)

  protected val period = 5 seconds

  private def fixIncompleteCheckpoints() =
    for (doc <- db.view("bib_input", "incomplete-checkpoints").execute.values[JObject]) {
      try {
	val JInt(bib) = doc \ "bib"
	try {
	  val JInt(race) = db("contestant-" + bib).execute()("course")
	  if (race != 0) {
	    log.info("fixing incomplete race " + race + " for bib " + bib)
	    db.insert(doc.replace("race_id" :: Nil, JInt(race))).execute
	  }
	} catch {
	    case StatusCode(404, _) =>
	      log.debug("no information available for contestant " + bib)
	}
      } catch {
	  case e: Exception =>
	    log.warning("unable to fix contestant: " + e)
      }
    }


  override def periodic() =
    try {
      fixIncompleteCheckpoints()
    } catch {
      case e: Exception =>
	log.warning("unable to get incomplete checkpoints: " + e)
    }

}
