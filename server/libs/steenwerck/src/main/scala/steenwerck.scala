import java.io.File

import akka.actor.ActorSystem
import com.typesafe.config.{ConfigParseOptions, ConfigException, Config, ConfigFactory}
import net.rfc1149.canape._
import play.api.libs.json.{JsBoolean, JsObject, JsValue, Json}

import net.ceedubs.ficus.Ficus._

import scala.concurrent.{ExecutionContext, Future}

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

  def testsAllowed(db: Database)(implicit context: ExecutionContext): Future[Boolean] =
    db("configuration").map(d => (d \ "tests_allowed") == JsBoolean(true))

  def upper(levels: Int, baseName: String): String = levels match {
    case 0 => baseName
    case n => s"..${File.separator}${upper(n-1, baseName)}"
  }

  lazy val config : Config = {
    val options = ConfigParseOptions.defaults().setAllowMissing(false)
    var baseName = "steenwerck"
    (0 to 3).foldLeft(None: Option[Config]) {
      case (s@Some(config), _) =>
        s
      case (None, level) =>
        try {
          Some(ConfigFactory.parseFileAnySyntax(new File(upper(level, baseName)), options))
        } catch {
          case e: ConfigException =>
            None
        }
      } getOrElse ConfigFactory.empty()
    }

  def couchFromConfig(basePath: String, actorSystem: ActorSystem, auth: Option[(String, String)] = None): Couch =
    new Couch(host = config.as[Option[String]](s"$basePath.host").getOrElse("localhost"),
              port = config.as[Option[Int]](s"$basePath.port").getOrElse(5984),
              auth = auth orElse config.as[Option[String]](s"$basePath.user").flatMap(user =>
                config.as[Option[String]](s"$basePath.password").map((user, _))))(actorSystem)

  def localCouch(implicit actorSystem: ActorSystem): Couch =
    couchFromConfig("steenwerck.local", actorSystem)

  val localDbName = "steenwerck100km"

  def masterCouch(auth: Option[(String, String)] = None)(implicit actorSystem: ActorSystem): Couch =
    couchFromConfig("steenwerck.master", actorSystem, auth)

}
