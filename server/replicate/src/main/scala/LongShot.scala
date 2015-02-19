import akka.event.Logging
import net.liftweb.json._
import net.rfc1149.canape._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

import Global._

class LongShot(db: Database) extends PeriodicTask(300 seconds) with LoggingError {

  import implicits._

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
      val future = Future.sequence(toDelete map { db.delete(_).toFuture })
      future onSuccess {
        case _ => if (!toDelete.isEmpty) log.info("succesfully deleted obsolete transient documents (" + toDelete.size + ")")
      }
      future
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
