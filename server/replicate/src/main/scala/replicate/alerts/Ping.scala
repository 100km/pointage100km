package replicate.alerts

import net.rfc1149.canape.Database
import play.api.libs.json.JsObject

import scala.concurrent.{ExecutionContext, Future}

object Ping {

  def lastPing(siteId: Int, database: Database)(implicit ec: ExecutionContext): Future[Option[Long]] =
    database.view[Int, JsObject]("admin", "alive",
      Seq("startkey" -> siteId.toString, "endkey" -> siteId.toString, "group" -> "true")).map { rows =>
        rows.headOption.map(row => (row._2 \ "max").as[Long])
    }

}
