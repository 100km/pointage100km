import java.io.File

import akka.Done
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.ConfigException.IO
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions}
import net.ceedubs.ficus.Ficus._
import net.rfc1149.canape._
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}

package object steenwerck {

  private val uuid = java.util.UUID.randomUUID

  def forceUpdate[T](db: Database, id: String, data: T)(implicit ev: T => JsObject, fm: Materializer): Future[Done] = {
    implicit val ec = fm.executionContext
    db.updateForm("bib_input", "force-update", id, Map("json" -> Json.stringify(data))).map(Couch.checkStatus).map(_ => Done)
  }

  private def makePing(siteId: Int, time: Long) =
    Json.obj("type" -> "ping", "site_id" -> siteId, "time" -> time)

  private def pingId(siteId: Int) = s"ping-$siteId-$uuid"

  def ping(db: Database, siteId: Int)(implicit fm: Materializer): Future[Done] =
    forceUpdate(db, pingId(siteId), makePing(siteId, System.currentTimeMillis))

  def message(db: Database, msg: String)(implicit fm: Materializer): Future[Done] =
    forceUpdate(db, "status", Json.obj("type" -> "status", "scope" -> "local", "message" -> msg))

  def testsAllowed(db: Database)(implicit context: ExecutionContext): Future[Boolean] =
    db("configuration").map(d => (d \ "tests_allowed").as[Boolean])

  def upper(levels: Int, baseName: String): String = levels match {
    case 0 => baseName
    case n => s"..${File.separator}${upper(n - 1, baseName)}"
  }

  lazy val steenwerckRootConfig: Config = {
    val options = ConfigParseOptions.defaults().setAllowMissing(false)
    var baseName = "steenwerck"
    (0 to 3).foldLeft(None: Option[Config]) {
      case (s@Some(config), _) =>
        s
      case (None, level) =>
        try {
          Some(ConfigFactory.parseFileAnySyntax(new File(upper(level, baseName)), options))
        } catch {
          case _: IO =>
            None
        }
    } getOrElse ConfigFactory.empty()
  }.withFallback(ConfigFactory.load())

  def couchFromConfig(config: Config, basePath: String, actorSystem: ActorSystem, auth: Option[(String, String)] = None): Couch =
    new Couch(
      host   = config.as[Option[String]](s"$basePath.host").getOrElse("localhost"),
      port   = config.as[Option[Int]](s"$basePath.port").getOrElse(5984),
      secure = config.as[Option[Boolean]](s"$basePath.secure").getOrElse(false),
      auth   = auth orElse config.as[Option[String]](s"$basePath.user").flatMap(user =>
        config.as[Option[String]](s"$basePath.password").map((user, _))),
      config = config)(actorSystem)

  def localCouch(config: Config = steenwerckRootConfig)(implicit actorSystem: ActorSystem): Couch =
    couchFromConfig(config, "steenwerck.local", actorSystem)

  def proxyUrl(implicit actorSystem: ActorSystem): Option[String] =
    steenwerckRootConfig.as[Option[String]]("steenwerck.master.proxy-url")

  val localDbName = "steenwerck100km"

  def masterCouch(config: Config = steenwerckRootConfig, auth: Option[(String, String)] = None)(implicit actorSystem: ActorSystem): Couch =
    couchFromConfig(config, "steenwerck.master", actorSystem, auth)

}
