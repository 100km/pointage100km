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
    db.view[JsValue, JsObject]("admin", "transient-docs") flatMap { docs =>
      val toDelete = docs map (_._2) filter {
        _ \ "time" match {
          case JsNumber(time) if time < deadline => true
          case _ => false
        }
      }
      val future = Future.traverse(toDelete)(db.delete)
      future.onSuccess {
        case _ => if (toDelete.nonEmpty) log.info("successfully deleted obsolete transient documents (" + toDelete.size + ")")
      }
      future
    }
  }

}
