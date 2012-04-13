import akka.dispatch.{Await, Future}
import akka.event.Logging
import akka.util.Duration
import akka.util.duration._
import net.liftweb.json._
import net.rfc1149.canape._

import Global._

class LongShot(db: Database) extends PeriodicTask(300 seconds) with LoggingError {

  private[this] implicit val formats = DefaultFormats

  override val log = Logging(system, "longShot")

  private[this] def checkForObsolete() = {
    val deadline = System.currentTimeMillis - 3600000   // 1 hour ago is old
    val docs = db.view("admin", "transient-docs").toFuture map {
      _.values[JObject] filter { _ \ "time" match {
        case JInt(time) if time < deadline => true
        case _                             => false
      } }
    }
    docs flatMap { toDelete =>
      Future.sequence(toDelete map { db.delete(_).toFuture }) onSuccess {
        case _ => if (!toDelete.isEmpty) log.info("succesfully deleted obsolete transient documents (" + toDelete.size + ")")
      }
    }
  }

  override def act() {
    try {
      Await.ready(checkForObsolete, Duration.Inf)
    } catch {
      case e: Exception =>
	log.warning("error when deleting obsolete documents: " + e)
    }
  }

}
