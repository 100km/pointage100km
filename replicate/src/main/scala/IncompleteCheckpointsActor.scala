import akka.actor.Actor
import akka.dispatch.{Future, Promise}
import akka.event.Logging
import akka.util.duration._
import net.liftweb.json._
import net.rfc1149.canape._

import FutureUtils._
import Global._

class IncompleteCheckpointsActor(db: Database) extends Actor {

  import implicits._

  private val log = Logging(context.system, this)

  private def fixIncompleteCheckpoints() =
    for (r <- db.view("bib_input", "incomplete-checkpoints").futureExecute)
      yield Future.traverse(r.values[JObject]) { doc =>
	val JInt(bib) = doc \ "bib"
	db("contestant-" + bib).futureExecute flatMap { r =>
	  val JInt(race) = r("course")
	  if (race != 0) {
	    log.info("fixing incomplete race " + race + " for bib " + bib)
	    db.insert(doc.replace("race_id" :: Nil, JInt(race))).futureExecute recover {
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

  override def receive = {
      case 'act =>
	fixIncompleteCheckpoints() onFailure {
	  case e: Exception =>
	    log.warning("unable to get incomplete checkpoints: " + e)
	} onComplete {
	  case _ => context.system.scheduler.scheduleOnce(5 seconds, self, 'act)
	}
  }

  override def preStart() =
    self ! 'act

}
