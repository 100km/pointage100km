package replicate.alerts

import net.rfc1149.canape.Database
import play.api.libs.json.{JsValue, Json, Reads}

import scala.concurrent.{Future, ExecutionContext}

case class Broadcast(_id: String, message: String, target: Option[Int], addedTS: Long, deletedTS: Option[Long])

object Broadcast {

 def broadcasts(database: Database, since: Option[Long])(implicit ec: ExecutionContext): Future[Seq[(Long, Broadcast)]] =
  database.view[Long, Broadcast]("admin", "messages-sorted-by-ts", since.map("startkey" -> _.toString).toSeq)

 implicit val broadcastReads: Reads[Broadcast] = Json.reads[Broadcast]

}
