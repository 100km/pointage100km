import net.rfc1149.canape._
import play.api.libs.json.{JsNumber, JsObject, JsValue}

import scala.concurrent.Future
import scala.language.postfixOps

class RemoveObsoleteDocuments(db: Database) extends PeriodicTaskActor {

  import Global._

  override val period = obsoleteRemoveInterval

  private[this] val obsoleteMillisecons = obsoleteAge.toMillis

  override def future: Future[Seq[JsValue]] = {
    val deadline = System.currentTimeMillis - obsoleteMillisecons
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

}
