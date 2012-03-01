import akka.dispatch.Future
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._
import net.rfc1149.canape.helpers._

package object steenwerck {

  private implicit val formats = DefaultFormats

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

  private def makePing(siteId: Int, time: Long) =
    Map("type" -> JString("ping"), ("site_id" -> JInt(siteId)), ("time" -> JInt(time)))

  private def pingConflictSolver(documents: Seq[mapObject]): mapObject = {
    val first = documents.head
    makePing(first("site-id").extract[Int],
	     documents.map{ _("time").extract[Long] }.max) + ("rev" -> first("rev"))
  }

  private def pingId(siteId: Int) = "ping-site" + siteId

  def ping(db: Database, siteId: Int): CouchRequest[JValue] =
    forceUpdate(db, "ping-site" + siteId, makePing(siteId, System.currentTimeMillis))

  def pingResolve(db: Database, siteId: Int): Future[JValue] = {
    ping(db, siteId).toFuture recoverWith {
      case StatusCode(409, _) => {
	val id = pingId(siteId)
	getConflictingRevs(db, id).toFuture() flatMap {
	  getRevs(db, id, _).toFuture
	} flatMap {
	  solve(db, _)(pingConflictSolver).toFuture
	}
      }
    }
  }

  def message(db: Database, msg: String): CouchRequest[JValue] =
    forceUpdate(db, "_local/status", ("type" -> "message") ~ ("message" -> msg)).thenTouch(db)

}
