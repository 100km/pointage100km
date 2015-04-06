import Global._
import akka.event.Logging
import net.rfc1149.canape._
import play.api.libs.json.{JsNumber, JsObject}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class LongShot(db: Database) extends PeriodicTask(300 seconds) with LoggingError {

  override val log = Logging(system, "longShot")

  private[this] def checkForObsolete() = {
    val deadline = System.currentTimeMillis - 3600000   // 1 hour ago is old
    val docs = db.view("admin", "transient-docs") map {
      _.values[JsObject] filter { _ \ "time" match {
        case JsNumber(time) if time < deadline => true
        case _                                 => false
      } }
    }
    docs flatMap { toDelete =>
      val future = Future.sequence(toDelete map { db.delete })
      future onSuccess {
        case _ => if (toDelete.nonEmpty) log.info("successfully deleted obsolete transient documents (" + toDelete.size + ")")
      }
      future
    }
  }

  override def act() {
    try {
      Await.ready(checkForObsolete(), Duration.Inf)
    } catch {
      case e: Exception =>
        log.warning("error when deleting obsolete documents: " + e)
    }
  }

}
