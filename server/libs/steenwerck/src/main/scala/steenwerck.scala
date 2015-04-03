import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._
import scala.concurrent.Future

package object steenwerck {

  private implicit val formats = DefaultFormats

  private val uuid = java.util.UUID.randomUUID

  def forceUpdate[T](db: Database, id: String, data: T)(implicit ev: T => JObject): Future[JValue] =
    db.update("bib_input", "force-update", id, Map("json" -> compact(render(data))))

  private def makePing(siteId: Int, time: Long) =
    Map("type" -> JString("ping"), "site_id" -> JInt(siteId), "time" -> JInt(time))

  private def pingId(siteId: Int) = s"ping-site$siteId-$uuid"

  def ping(db: Database, siteId: Int): Future[JValue] =
    forceUpdate(db, pingId(siteId), makePing(siteId, System.currentTimeMillis))

  def message(db: Database, msg: String): Future[JValue] =
    forceUpdate(db, "status",
		("type" -> "status") ~
		("scope" -> "local") ~
		("message" -> msg))

}
