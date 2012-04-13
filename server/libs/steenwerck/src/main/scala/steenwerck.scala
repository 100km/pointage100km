import akka.dispatch.Future
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._
import net.rfc1149.canape.helpers._

package object steenwerck {

  private implicit val formats = DefaultFormats

  private val uuid = java.util.UUID.randomUUID

  def forceUpdate[T <% JObject](db: Database, id: String, data: T): CouchRequest[JValue] =
    db.update("bib_input", "force-update", id,
	      Map("json" -> compact(render(data))))

  private def makePing(siteId: Int, time: Long) =
    Map("type" -> JString("ping"), ("site_id" -> JInt(siteId)), ("time" -> JInt(time)))

  private def pingId(siteId: Int) = "ping-site" + siteId + "-" + uuid

  def ping(db: Database, siteId: Int): CouchRequest[JValue] =
    forceUpdate(db, pingId(siteId), makePing(siteId, System.currentTimeMillis))

  def message(db: Database, msg: String): CouchRequest[JValue] =
    forceUpdate(db, "status",
		("type" -> "status") ~
		("scope" -> "local") ~
		("message" -> msg))

}
