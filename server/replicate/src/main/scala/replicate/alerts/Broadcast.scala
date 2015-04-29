package replicate.alerts

import net.rfc1149.canape.Database
import play.api.libs.json.{JsValue, Json, Reads}

import scala.concurrent.{Future, ExecutionContext}

case class Broadcast(_id: String, message: String, target: Option[Int], addedTS: Long, deletedTS: Option[Long])

object Broadcast {

 def broadcasts(database: Database)(implicit ec: ExecutionContext): Future[Seq[Broadcast]] =
  database.view[JsValue, Broadcast]("common", "messages-sorted-per-site").map(_.map(_._2))

 implicit val broadcastReads: Reads[Broadcast] = Json.reads[Broadcast]

}
