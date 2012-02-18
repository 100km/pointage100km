import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._

package object steenwerck {

  def forceUpdate[T <% JObject](db: Database, id: String, data: T): CouchRequest[JValue] =
    db.update("bib_input", "force-update", id,
	      Map("json" -> compact(render(data))))

  def touchMe(db: Database): CouchRequest[JValue] =
    forceUpdate(db, "touch_me", ("type" -> "touch-me"))

  implicit def touchIt[T <: AnyRef : Manifest](request: CouchRequest[T]) = new {
    def thenTouch(db: Database) = request.map { result =>
      touchMe(db).toFuture
      result
    }
  }

  def ping(db: Database, siteId: Int): CouchRequest[JValue] =
    forceUpdate(db, "ping-site" + siteId,
      ("type" -> "ping") ~ ("site-id" -> siteId) ~ ("time" -> System.currentTimeMillis))

  def message(db: Database, msg: String): CouchRequest[JValue] =
    forceUpdate(db, "_local/status", ("type" -> "message") ~ ("message" -> msg)).thenTouch(db)

}
