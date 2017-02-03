package replicate.maintenance

import akka.event.LoggingAdapter
import net.rfc1149.canape._
import play.api.libs.json.{JsObject, JsValue}
import replicate.utils.Global

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

object RemoveObsoleteDocuments {

  private[this] val obsoleteMillisecons = Global.obsoleteAge.toMillis

  def removeObsoleteDocuments(db: Database, log: LoggingAdapter)(implicit ec: ExecutionContext): Future[Seq[String]] = {
    val deadline = System.currentTimeMillis - obsoleteMillisecons
    db.view[JsValue, JsObject]("admin", "transient-docs") flatMap { docs ⇒
      val toDelete = docs map (_._2) filter { js ⇒
        (js \ "time").asOpt[Long] match {
          case Some(time) ⇒ time < deadline
          case None       ⇒ false
        }
      }
      val future = Future.traverse(toDelete)(db.delete)
      future.foreach(_ ⇒ if (toDelete.nonEmpty) log.info("successfully deleted obsolete transient documents ({})", toDelete.size))
      future
    }
  }

}
