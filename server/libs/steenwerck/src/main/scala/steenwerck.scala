import net.rfc1149.canape._
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.concurrent.Future

package object steenwerck {

  private val uuid = java.util.UUID.randomUUID

  def forceUpdate[T](db: Database, id: String, data: T)(implicit ev: T => JsObject): Future[JsValue] =
    db.update("bib_input", "force-update", id, Map("json" -> Json.stringify(data)))

  private def makePing(siteId: Int, time: Long) =
    Json.obj("type" -> "ping", "site_id" -> siteId, "time" -> time)

  private def pingId(siteId: Int) = s"ping-site$siteId-$uuid"

  def ping(db: Database, siteId: Int): Future[JsValue] =
    forceUpdate(db, pingId(siteId), makePing(siteId, System.currentTimeMillis))

  def message(db: Database, msg: String): Future[JsValue] =
    forceUpdate(db, "status", Json.obj("type" -> "status", "scope" -> "local", "message" -> msg))

}
